package local.train.blockwindow;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import local.train.blockwindow.service.BlockWindowService;
import local.train.blockwindow.service.LateStartRejectedException;
import local.train.blockwindow.service.PreconditionNotMetException;
import local.train.blockwindow.web.MissingCondition;
import local.train.blockwindow.web.Requests.ReleaseCheckRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.sql.DataSource;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 真实 PostgreSQL（embedded，无需外部服务/Docker）上的集成测试：
 * Flyway 迁移（text[]/tstzrange/GIST）、跨午夜窗口、两计划并发竞争同一区段（FOR UPDATE 串行化）、
 * 开工确认重试、资质失效、迟到消息、销记逐项复核与撤离。
 *
 * <p>必须在 Spring 上下文创建前启动嵌入式库，故用静态初始化设置数据源连接参数。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(EmbeddedPostgresIT.ControllableClockConfig.class)
class EmbeddedPostgresIT {

    static EmbeddedPostgres pg;

    static {
        try {
            pg = EmbeddedPostgres.builder().setServerConfig("timezone", "UTC").start();
            System.setProperty("spring.datasource.url", pg.getJdbcUrl("postgres", "postgres"));
            System.setProperty("spring.datasource.username", "postgres");
            System.setProperty("spring.datasource.password", "postgres");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @AfterAll
    static void stopDb() throws IOException {
        if (pg != null) {
            pg.close();
        }
    }

    /** 可在测试中拨动的时钟，替代系统 UTC 时钟。 */
    static class ControllableClock extends Clock {
        volatile Instant now = Instant.parse("2026-09-30T22:31:00Z");

        @Override public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override public Instant instant() {
            return now;
        }
    }

    @TestConfiguration
    static class ControllableClockConfig {
        @Bean
        @Primary
        Clock controllableClock() {
            return new ControllableClock();
        }
    }

    @Autowired BlockWindowService service;
    @Autowired DataSource dataSource;
    @Autowired TestRestTemplate http;

    private static final Instant S = Instant.parse("2026-09-30T22:30:00Z");
    private static final Instant E = Instant.parse("2026-10-01T01:30:00Z");

    private void create(String planNo, String workType, List<String> secs) {
        service.createPlan(planNo, "演练-" + planNo, workType, secs, S, E, "ZONE-A");
    }

    /** 用指定 4 名（已存在的）人员完成派工并逐项确认保护。 */
    private void readyAll(String planNo, String wl, String so, String ct, String pt) {
        service.assign(planNo, wl, "WORK_LEADER");
        service.assign(planNo, so, "SAFETY_OFFICER");
        service.assign(planNo, ct, "CONTACT");
        service.assign(planNo, pt, "PROTECTOR");
        var w = service.requireWindow(planNo);
        service.protectionsFor(w.getId()).forEach(pr -> service.confirmProtection(pr.getId(), pt));
    }

    private void reviewAndEvacuate(String planNo, List<String> badges) {
        var w = service.requireWindow(planNo);
        service.protectionsFor(w.getId()).forEach(pr ->
                service.submitReleaseCheck(planNo, new ReleaseCheckRequest(
                        "REVIEW", pr.getSectionCode() + "/" + pr.getRequirementCode(),
                        pr.getSectionCode(), pr.getLabel() + "撤除复核", badges.get(1), true)));
        badges.forEach(badge -> service.submitReleaseCheck(planNo, new ReleaseCheckRequest(
                "EVACUATION", badge, null, badge + " 已撤离", badges.get(1), true)));
    }

    @Test
    @DisplayName("迁移真实生效：tstzrange 生成列与 GIST 索引存在")
    void migrationObjectsExist() throws Exception {
        try (var conn = dataSource.getConnection();
             var st = conn.createStatement()) {
            var rs = st.executeQuery("""
                    SELECT count(*) FROM pg_attribute a
                    JOIN pg_class c ON c.oid = a.attrelid
                    WHERE c.relname='block_window' AND a.attname='window_range'
                    """);
            rs.next();
            assertThat(rs.getLong(1)).isEqualTo(1);

            rs = st.executeQuery("""
                    SELECT count(*) FROM pg_indexes
                    WHERE tablename='block_window' AND indexname='idx_block_window_range_gist'
                    """);
            rs.next();
            assertThat(rs.getLong(1)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("跨午夜窗口：编制→派工→保护→开工→逐项复核撤离→销记；迟到消息不能重开")
    void crossMidnightHappyPath() {
        String planNo = "IT-CROSS";
        create(planNo, "CABLE_TEST", List.of("S02", "S03"));
        readyAll(planNo, "B1001", "B1002", "B1003", "B1004");

        var start = service.startWork(planNo, "it-start-key", "B1001");
        assertThat(start.started()).isTrue();
        assertThat(start.window().getStatus().name()).isEqualTo("IN_PROGRESS");
        assertThat(start.receipt().isSimulated()).isTrue();
        assertThat(start.receipt().getReceiptNo()).startsWith("SIM-DISP-");

        reviewAndEvacuate(planNo, List.of("B1001", "B1002", "B1003", "B1004"));
        var released = service.release(planNo, "it-release-key", "B1001");
        assertThat(released.released()).isTrue();
        assertThat(released.window().getStatus().name()).isEqualTo("RELEASED");

        // 迟到开工消息：只留痕拒绝，状态保持 RELEASED
        boolean accepted = service.acceptLateStartMessage(planNo, "LATE-1", "B1001");
        assertThat(accepted).isFalse();
        assertThat(service.requireWindow(planNo).getStatus().name()).isEqualTo("RELEASED");
        assertThatThrownBy(() -> service.startWork(planNo, "it-start-key", "B1001"))
                .isInstanceOf(LateStartRejectedException.class);
        assertThat(service.requireWindow(planNo).getStatus().name()).isEqualTo("RELEASED");
    }

    @Test
    @DisplayName("开工确认重试：同键重放返回同回执编号，不产生第二次开工")
    void startRetryIsIdempotent() {
        String planNo = "IT-IDEM";
        create(planNo, "ROUTINE_INSPECTION", List.of("S01"));
        readyAll(planNo, "B1001", "B1002", "B1003", "B1004");

        var first = service.startWork(planNo, "idem-77", "B1001");
        var second = service.startWork(planNo, "idem-77", "B1001");
        assertThat(first.started()).isTrue();
        assertThat(second.replayed()).isTrue();
        assertThat(second.receipt().getReceiptNo()).isEqualTo(first.receipt().getReceiptNo());
        assertThat(second.window().getStartedAt()).isEqualTo(first.window().getStartedAt());
    }

    @Test
    @DisplayName("两个计划并发竞争同一区段：行级锁串行化，一成一败，败者得到具体 MUTEX_MATRIX_CONFLICT")
    void concurrentCompetingPlansSerializeOnRowLock() throws Exception {
        // 为两个并发计划各造一套资质人员（TRACK_WORK / SIGNAL_REPLACEMENT）
        create("IT-A", "TRACK_WORK", List.of("S03"));
        create("IT-B", "SIGNAL_REPLACEMENT", List.of("S03"));
        readyAll("IT-A", "PA-WL", "PA-SO", "PA-CT", "PA-PT");
        readyAll("IT-B", "PB-WL", "PB-SO", "PB-CT", "PB-PT");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicReference<String> winner = new AtomicReference<>();
        AtomicReference<PreconditionNotMetException> loserError = new AtomicReference<>();
        AtomicReference<Exception> unexpected = new AtomicReference<>();

        Future<?> fa = pool.submit(() -> race(bothReady, go, "IT-A", "conc-A", "PA-WL",
                winner, loserError, unexpected));
        Future<?> fb = pool.submit(() -> race(bothReady, go, "IT-B", "conc-B", "PB-WL",
                winner, loserError, unexpected));

        assertThat(bothReady.await(5, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        fa.get(20, TimeUnit.SECONDS);
        fb.get(20, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(unexpected.get()).isNull();
        assertThat(winner.get()).isNotNull();
        assertThat(loserError.get()).isNotNull();
        MissingCondition conflict = loserError.get().getMissingConditions().stream()
                .filter(m -> m.code().equals("MUTEX_MATRIX_CONFLICT"))
                .findFirst().orElseThrow();
        assertThat(conflict.detail().get("otherPlanNo")).isEqualTo(winner.get());
        assertThat(conflict.detail().get("sharedSections")).isEqualTo(List.of("S03"));
    }

    private void race(CountDownLatch bothReady, CountDownLatch go, String planNo, String key,
                      String badge, AtomicReference<String> winner,
                      AtomicReference<PreconditionNotMetException> loserError,
                      AtomicReference<Exception> unexpected) {
        bothReady.countDown();
        try {
            go.await();
            var out = service.startWork(planNo, key, badge);
            if (out.started()) {
                winner.set(planNo);
            }
        } catch (PreconditionNotMetException e) {
            loserError.set(e);
        } catch (Exception e) {
            unexpected.set(e);
        }
    }

    @Test
    @DisplayName("矩阵兼容的作业同区段并行：TRACK_WORK 已作业时 CABLE_TEST 仍可开工")
    void compatibleTypesMayRunTogether() {
        create("IT-T", "TRACK_WORK", List.of("S04"));
        create("IT-C", "CABLE_TEST", List.of("S04"));
        readyAll("IT-T", "B1001", "B1002", "B1003", "B1004");
        // CABLE_TEST 需要具备该资质的人员——种子中 B1001~B1003 覆盖，B1004 不覆盖，造一名防护员
        readyAll("IT-C", "B1001", "B1002", "B1003", "PC-PT");

        assertThat(service.startWork("IT-T", "t1", "B1001").started()).isTrue();
        assertThat(service.startWork("IT-C", "c1", "B1001").started()).isTrue();
    }

    @Test
    @DisplayName("资质不覆盖作业类型：派工即被拒并要求重新安排")
    void wrongQualificationRejectedAtAssignment() {
        create("IT-Q", "TRACK_WORK", List.of("S01"));
        // B1004（现场防护员）的 qualified_work 不含 TRACK_WORK... 实际种子含；改用不存在资质组合：
        // 种子中 B1005 仅可 SIGNAL_REPLACEMENT/CABLE_TEST，派到 TRACK_WORK 必须被拒
        assertThatThrownBy(() -> service.assign("IT-Q", "B1005", "WORK_LEADER"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("不具备");
    }

    @Test
    @DisplayName("失败是逐项缺失条件而非统一审批失败：缺岗位 + 6 项保护未确认")
    void failureListsEachMissingCondition() {
        create("IT-F", "TRACK_WORK", List.of("S01", "S02"));
        service.assign("IT-F", "B1001", "WORK_LEADER"); // 只有作业负责人，保护全部未确认
        assertThatThrownBy(() -> service.startWork("IT-F", "f1", "B1001"))
                .isInstanceOf(PreconditionNotMetException.class)
                .satisfies(e -> {
                    var codes = ((PreconditionNotMetException) e).getMissingConditions()
                            .stream().map(MissingCondition::code).toList();
                    assertThat(codes).contains("ROLE_NOT_ASSIGNED", "PROTECTION_NOT_CONFIRMED");
                    long protectionItems = ((PreconditionNotMetException) e).getMissingConditions().stream()
                            .filter(m -> m.code().equals("PROTECTION_NOT_CONFIRMED")).count();
                    assertThat(protectionItems).isEqualTo(6); // 2 区段 × 3 默认项
                });
    }

    @Test
    @DisplayName("HTTP 冒烟：springdoc 与手写 OpenAPI 端点可访问")
    void openApiEndpointsServed() {
        ResponseEntity<String> yaml = http.getForEntity("/openapi.yaml", String.class);
        assertThat(yaml.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(yaml.getBody()).contains("openapi: 3.0.3", "互斥作业矩阵", "missingConditions");

        ResponseEntity<Map> docs = http.getForEntity("/v3/api-docs", Map.class);
        assertThat(docs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(String.valueOf(docs.getBody())).contains("/api/plans", "开工确认");

        ResponseEntity<String> ui = http.getForEntity("/swagger-ui/index.html", String.class);
        assertThat(ui.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    @DisplayName("HTTP 冒烟：未满足条件时 /start 返回 HTTP 422 且逐项列出缺失条件")
    @SuppressWarnings("unchecked")
    void httpStartFailureIs422WithItemizedConditions() {
        String planNo = "IT-HTTP-422";
        create(planNo, "ROUTINE_INSPECTION", List.of("S01"));
        // 不派工、不确认保护
        ResponseEntity<Map> resp = http.postForEntity(
                "/api/plans/" + planNo + "/start",
                Map.of("idempotencyKey", "http-k", "operatorBadge", "B1001"),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        Map<String, Object> body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("errorCode")).isEqualTo("START_PRECONDITION_NOT_MET");
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("missingConditions");
        assertThat(items).isNotEmpty();
        assertThat(items.stream().map(m -> m.get("code")).toList())
                .contains("ROLE_NOT_ASSIGNED", "PROTECTION_NOT_CONFIRMED");
        // 每一项都必须有面向学员的具体中文说明，而不是统一文案
        assertThat(items).allSatisfy(m ->
                assertThat(String.valueOf(m.get("message"))).doesNotContain("审批失败"));
    }
}
