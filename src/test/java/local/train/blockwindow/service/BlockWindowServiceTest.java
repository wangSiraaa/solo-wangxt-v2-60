package local.train.blockwindow.service;

import local.train.blockwindow.domain.BlockWindow;
import local.train.blockwindow.domain.ConfirmationRecord;
import local.train.blockwindow.domain.DispatchReceipt;
import local.train.blockwindow.domain.MutexRule;
import local.train.blockwindow.domain.Personnel;
import local.train.blockwindow.domain.PlanAssignment;
import local.train.blockwindow.domain.ProtectionRequirement;
import local.train.blockwindow.domain.RailSection;
import local.train.blockwindow.domain.WindowStatus;
import local.train.blockwindow.repo.BlockWindowRepository;
import local.train.blockwindow.repo.ConfirmationRecordRepository;
import local.train.blockwindow.repo.MutexRuleRepository;
import local.train.blockwindow.repo.PersonnelRepository;
import local.train.blockwindow.repo.PlanAssignmentRepository;
import local.train.blockwindow.repo.ProtectionRequirementRepository;
import local.train.blockwindow.repo.RailSectionRepository;
import local.train.blockwindow.web.MissingCondition;
import local.train.blockwindow.web.Requests.ReleaseCheckRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 开工/销记规则单元测试：不依赖数据库，仓储全部 mock。
 * 覆盖：跨午夜窗口、两计划竞争同一区段（矩阵判定）、开工确认重试幂等、
 * 资质在预计结束前失效、迟到消息、销记逐项复核/撤离。
 */
@ExtendWith(MockitoExtension.class)
class BlockWindowServiceTest {

    static final Instant T1 = Instant.parse("2026-09-30T22:30:00Z");
    static final Instant T2 = Instant.parse("2026-10-01T01:30:00Z"); // 跨午夜
    static final Instant FAR = Instant.parse("2030-01-01T00:00:00Z");

    @Mock BlockWindowRepository windowRepo;
    @Mock RailSectionRepository sectionRepo;
    @Mock PersonnelRepository personnelRepo;
    @Mock PlanAssignmentRepository assignmentRepo;
    @Mock ProtectionRequirementRepository protectionRepo;
    @Mock ConfirmationRecordRepository confirmationRepo;
    @Mock MutexRuleRepository mutexRuleRepo;
    @Mock DispatchSimulator dispatchSimulator;

    final MutableClock clock = new MutableClock(T1);
    BlockWindowService service;

    final AtomicLong windowIds = new AtomicLong(100);
    final AtomicLong personIds = new AtomicLong(1);
    final AtomicLong protectionIds = new AtomicLong(500);
    final List<BlockWindow> windowStore = new ArrayList<>();
    final List<Personnel> personStore = new ArrayList<>();
    final List<PlanAssignment> assignmentStore = new ArrayList<>();
    final List<ProtectionRequirement> protectionStore = new ArrayList<>();
    final List<ConfirmationRecord> confirmationStore = new ArrayList<>();
    final List<MutexRule> ruleStore = new ArrayList<>();

    /** 固定/可拨的时钟，模拟“可注入时钟”。 */
    static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant t) {
            this.now = t;
        }

        @Override public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override public Instant instant() {
            return now;
        }
    }

    @BeforeEach
    void setUp() {
        service = new BlockWindowService(windowRepo, sectionRepo, personnelRepo, assignmentRepo,
                protectionRepo, confirmationRepo, new MutexEvaluator(mutexRuleRepo),
                dispatchSimulator, clock);

        lenient().when(windowRepo.save(any(BlockWindow.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(windowRepo.saveAndFlush(any(BlockWindow.class))).thenAnswer(i -> {
            BlockWindow w = i.getArgument(0);
            setId(w, windowIds.incrementAndGet());
            windowStore.add(w);
            return w;
        });
        lenient().when(windowRepo.findByPlanNo(any())).thenAnswer(i ->
                windowStore.stream().filter(w -> w.getPlanNo().equals(i.getArgument(0))).findFirst());
        lenient().when(windowRepo.existsByPlanNo(any())).thenAnswer(i ->
                windowStore.stream().anyMatch(w -> w.getPlanNo().equals(i.getArgument(0))));
        lenient().when(windowRepo.findActiveOverlapping(any(), any(), anyList())).thenAnswer(i ->
                windowStore.stream()
                        .filter(w -> w.getStatus() != WindowStatus.RELEASED)
                        .filter(w -> {
                            Instant s = i.getArgument(0);
                            Instant e = i.getArgument(1);
                            List<String> statusNames = i.getArgument(2);
                            return statusNames.contains(w.getStatus().name())
                                    && w.getPlannedStart().isBefore(e) && s.isBefore(w.getPlannedEnd());
                        })
                        .toList());
        lenient().when(windowRepo.lockActiveOverlapping(any(), any(), anyList()))
                .thenAnswer(i -> windowRepo.findActiveOverlapping(
                        i.getArgument(0), i.getArgument(1), i.getArgument(2)));

        lenient().when(sectionRepo.findAllById(any())).thenAnswer(i -> {
            List<String> codes = new ArrayList<>();
            ((Iterable<String>) i.getArgument(0)).forEach(codes::add);
            return sections(codes);
        });

        lenient().when(personnelRepo.save(any(Personnel.class))).thenAnswer(i -> {
            Personnel p = i.getArgument(0);
            setPersonId(p, personIds.incrementAndGet());
            personStore.add(p);
            return p;
        });
        lenient().when(personnelRepo.findByBadge(any())).thenAnswer(i ->
                personStore.stream().filter(p -> p.getBadge().equals(i.getArgument(0))).findFirst());
        lenient().when(personnelRepo.findById(any())).thenAnswer(i ->
                personStore.stream().filter(p -> p.getId().equals(((Number) i.getArgument(0)).longValue()))
                        .findFirst());
        lenient().when(personnelRepo.findAllById(any())).thenAnswer(i -> {
            List<Long> ids = new ArrayList<>();
            ((Iterable<Long>) i.getArgument(0)).forEach(ids::add);
            return personStore.stream().filter(p -> ids.contains(p.getId())).toList();
        });

        lenient().when(assignmentRepo.save(any(PlanAssignment.class))).thenAnswer(i -> {
            PlanAssignment a = i.getArgument(0);
            assignmentStore.add(a);
            return a;
        });
        lenient().when(assignmentRepo.findByBlockWindowId(any())).thenAnswer(i ->
                assignmentStore.stream()
                        .filter(a -> a.getBlockWindowId().equals(((Number) i.getArgument(0)).longValue()))
                        .toList());

        lenient().when(protectionRepo.save(any(ProtectionRequirement.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(protectionRepo.saveAll(any())).thenAnswer(i -> {
            List<ProtectionRequirement> list = i.getArgument(0);
            list.forEach(this::assignProtectionId);
            protectionStore.addAll(list);
            return list;
        });
        lenient().when(protectionRepo.findById(any())).thenAnswer(i ->
                protectionStore.stream()
                        .filter(r -> r.getId().equals(((Number) i.getArgument(0)).longValue()))
                        .findFirst());
        lenient().when(protectionRepo.findByBlockWindowIdOrderBySectionCodeAscRequirementCodeAsc(any()))
                .thenAnswer(i -> protectionStore.stream()
                        .filter(r -> r.getBlockWindowId().equals(((Number) i.getArgument(0)).longValue()))
                        .toList());

        lenient().when(confirmationRepo.save(any(ConfirmationRecord.class))).thenAnswer(i -> {
            ConfirmationRecord c = i.getArgument(0);
            confirmationStore.add(c);
            return c;
        });
        lenient().when(confirmationRepo.findByBlockWindowIdAndRecordTypeOrderByRecordedAtAsc(any(), any()))
                .thenAnswer(i -> confirmationStore.stream()
                        .filter(c -> c.getBlockWindowId().equals(((Number) i.getArgument(0)).longValue())
                                && c.getRecordType().equals(i.getArgument(1)))
                        .toList());

        lenient().when(mutexRuleRepo.findAll()).thenReturn(ruleStore);

        lenient().when(dispatchSimulator.issueStartReceipt(any())).thenAnswer(i ->
                new DispatchReceipt("SIM-RCPT-START-" + i.getArgument(0, BlockWindow.class).getPlanNo(),
                        ((BlockWindow) i.getArgument(0)).getId(), T1, "DISPATCHED",
                        "模拟开工回执", "SIMULATED"));
        lenient().when(dispatchSimulator.issueReleaseReceipt(any())).thenAnswer(i ->
                new DispatchReceipt("SIM-RCPT-RELEASE-" + i.getArgument(0, BlockWindow.class).getPlanNo(),
                        ((BlockWindow) i.getArgument(0)).getId(), T1, "CLOSED",
                        "模拟销记回执", "SIMULATED"));
        lenient().when(dispatchSimulator.receiptsOf(any())).thenAnswer(i ->
                Stream.of("SIM-RCPT-START-X", "SIM-RCPT-RELEASE-X").map(n ->
                        new DispatchReceipt(n, ((Number) i.getArgument(0)).longValue(), T1,
                                n.contains("RELEASE") ? "CLOSED" : "DISPATCHD", "", "")).toList());
    }

    private List<RailSection> sections(List<String> codes) {
        Map<String, List<String>> adj = Map.of(
                "S01", List.of("S02"),
                "S02", List.of("S01", "S03"),
                "S03", List.of("S02", "S04"),
                "S04", List.of("S03"));
        return codes.stream().map(c -> new RailSection(c, c + "名", adj.getOrDefault(c, List.of()))).toList();
    }

    private BlockWindow newCrossMidnightPlan(String planNo, String workType, List<String> sec) {
        BlockWindow w = service.createPlan(planNo, "跨午夜演练", workType, sec, T1, T2, "ZONE-A");
        return w;
    }

    private Personnel addPerson(String badge, String role, String workType, Instant validUntil) {
        Personnel p = new Personnel(badge, badge + "员", role, "模拟资格",
                List.of(workType), Instant.parse("2025-01-01T00:00:00Z"), validUntil);
        return personnelRepo.save(p);
    }

    private void assignAllRoles(BlockWindow w, String workType, Instant validUntil) {
        assignRole(w, "B-WL", "WORK_LEADER", workType, validUntil);
        assignRole(w, "B-SO", "SAFETY_OFFICER", workType, validUntil);
        assignRole(w, "B-CT", "CONTACT", workType, validUntil);
        assignRole(w, "B-PT", "PROTECTOR", workType, validUntil);
    }

    private void assignRole(BlockWindow w, String badge, String role, String workType, Instant validUntil) {
        Personnel p = addPerson(badge, role, workType, validUntil);
        assignmentRepo.save(new PlanAssignment(w.getId(), p.getId(), role));
    }

    private void confirmAllProtections(long windowId) {
        protectionStore.stream()
                .filter(r -> r.getBlockWindowId() == windowId)
                .forEach(r -> {
                    r.confirm("B-PT", clock.instant());
                });
    }

    private void setId(BlockWindow w, long id) {
        try {
            var f = BlockWindow.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(w, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setPersonId(Personnel p, long id) {
        try {
            var f = Personnel.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(p, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void assignProtectionId(ProtectionRequirement r) {
        try {
            var f = ProtectionRequirement.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(r, protectionIds.incrementAndGet());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ------------------------------------------------------------------
    // 场景一：跨午夜窗口
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("跨午夜窗口")
    class CrossMidnight {

        @Test
        @DisplayName("UTC 22:30 至次日 01:30 被识别为跨午夜，且在时间窗内可正常开工销记")
        void crossMidnightWindowFlowsEndToEnd() {
            assertThat(BlockWindowService.crossesMidnightUtc(T1, T2)).isTrue();

            BlockWindow w = newCrossMidnightPlan("BW-CROSS", "SIGNAL_REPLACEMENT", List.of("S02", "S03"));
            assignAllRoles(w, "SIGNAL_REPLACEMENT", FAR);
            confirmAllProtections(w.getId());
            clock.set(T1.plusSeconds(60)); // 已到开窗后

            BlockWindowService.StartOutcome started = service.startWork("BW-CROSS", "key-cross-1", "B-WL");
            assertThat(started.started()).isTrue();
            assertThat(w.getStatus()).isEqualTo(WindowStatus.IN_PROGRESS);

            // 销记：逐项复核（6 项保护）+ 逐人撤离（4 人）
            protectionsReviewAll(w);
            evacuateAll(w);
            BlockWindowService.ReleaseOutcome released = service.release("BW-CROSS", "rel-cross-1", "B-WL");
            assertThat(released.released()).isTrue();
            assertThat(w.getStatus()).isEqualTo(WindowStatus.RELEASED);
            assertThat(w.getReleasedAt()).isEqualTo(T1.plusSeconds(60));
        }

        @Test
        @DisplayName("非跨午夜窗口 crossesMidnightUtc=false，逻辑不受午夜影响")
        void sameNightWindowNotCross() {
            Instant a = Instant.parse("2026-09-30T20:00:00Z");
            Instant b = Instant.parse("2026-09-30T23:00:00Z");
            assertThat(BlockWindowService.crossesMidnightUtc(a, b)).isFalse();
        }
    }

    private void protectionsReviewAll(BlockWindow w) {
        for (ProtectionRequirement r : protectionStore.stream()
                .filter(r -> r.getBlockWindowId() == w.getId()).toList()) {
            service.submitReleaseCheck(w.getPlanNo(), new ReleaseCheckRequest(
                    "REVIEW", r.getSectionCode() + "/" + r.getRequirementCode(),
                    r.getSectionCode(), r.getLabel() + "撤除复核", "B-SO", true));
        }
    }

    private void evacuateAll(BlockWindow w) {
        List<PlanAssignment> mine = assignmentStore.stream()
                .filter(a -> a.getBlockWindowId() == w.getId()).toList();
        for (PlanAssignment a : mine) {
            Personnel p = personStore.stream().filter(x -> x.getId() == a.getPersonnelId()).findFirst().orElseThrow();
            service.submitReleaseCheck(w.getPlanNo(), new ReleaseCheckRequest(
                    "EVACUATION", p.getBadge(), null, p.getName() + "已撤离", "B-SO", true));
        }
    }

    // ------------------------------------------------------------------
    // 场景二：两个计划竞争同一区段 —— 必须按互斥矩阵，而非设备编号
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("两个计划竞争同一区段")
    class CompetingPlans {

        @Test
        @DisplayName("TRACK_WORK 与 SIGNAL_REPLACEMENT 同区段同时间：命中矩阵，先开工成功、后开工收到 MUTEX_MATRIX_CONFLICT")
        void mutexMatrixBlocksSecondPlan() {
            ruleStore.add(new MutexRule("TRACK_WORK", "SIGNAL_REPLACEMENT", "机具侵界"));

            BlockWindow track = newCrossMidnightPlan("BW-TRACK", "TRACK_WORK", List.of("S03"));
            BlockWindow signal = newCrossMidnightPlan("BW-SIGNAL", "SIGNAL_REPLACEMENT", List.of("S03", "S04"));

            assignAllRoles(track, "TRACK_WORK", FAR);
            assignAllRoles(signal, "SIGNAL_REPLACEMENT", FAR);
            confirmAllProtections(track.getId());
            confirmAllProtections(signal.getId());
            clock.set(T1.plusSeconds(30));

            // 计划 A 先开工
            assertThat(service.startWork("BW-TRACK", "k-track", "B-WL").started()).isTrue();

            // 计划 B：时间重叠 + 共用 S03 + 命中矩阵 → 必须被拦截，且给出具体冲突计划
            assertThatThrownBy(() -> service.startWork("BW-SIGNAL", "k-signal", "B-WL"))
                    .isInstanceOf(PreconditionNotMetException.class)
                    .satisfies(e -> {
                        PreconditionNotMetException pe = (PreconditionNotMetException) e;
                        assertThat(pe.getMissingConditions()).extracting(MissingCondition::code)
                                .contains("MUTEX_MATRIX_CONFLICT");
                        MissingCondition c = pe.getMissingConditions().stream()
                                .filter(m -> m.code().equals("MUTEX_MATRIX_CONFLICT")).findFirst().orElseThrow();
                        assertThat(c.detail().get("otherPlanNo")).isEqualTo("BW-TRACK");
                        assertThat(c.detail().get("sharedSections")).isEqualTo(List.of("S03"));
                    });
        }

        @Test
        @DisplayName("CABLE_TEST 与 TRACK_WORK 同区段同时间但矩阵空白：允许并行，不允许只比区段编号判冲突")
        void overlapWithoutMatrixEntryIsAllowed() {
            ruleStore.add(new MutexRule("TRACK_WORK", "TRACK_WORK", "自身互斥"));

            BlockWindow track = newCrossMidnightPlan("BW-TRACK-2", "TRACK_WORK", List.of("S03"));
            BlockWindow cable = newCrossMidnightPlan("BW-CABLE", "CABLE_TEST", List.of("S03"));

            assignAllRoles(track, "TRACK_WORK", FAR);
            assignAllRoles(cable, "CABLE_TEST", FAR);
            confirmAllProtections(track.getId());
            confirmAllProtections(cable.getId());
            clock.set(T1.plusSeconds(30));

            assertThat(service.startWork("BW-TRACK-2", "k1", "B-WL").started()).isTrue();
            // 矩阵没有 TRACK_WORK<->CABLE_TEST：即使设备/区段编号完全相同也不冲突
            BlockWindowService.StartOutcome out = service.startWork("BW-CABLE", "k2", "B-WL");
            assertThat(out.started()).isTrue();
            assertThat(out.window().getStatus()).isEqualTo(WindowStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("同类型 TRACK_WORK 自身互斥：共用 S02 时第二个被拦")
        void selfMutexTypeBlocked() {
            ruleStore.add(new MutexRule("TRACK_WORK", "TRACK_WORK", "线路作业自身互斥"));

            BlockWindow a = newCrossMidnightPlan("BW-A", "TRACK_WORK", List.of("S02"));
            BlockWindow b = newCrossMidnightPlan("BW-B", "TRACK_WORK", List.of("S02"));
            assignAllRoles(a, "TRACK_WORK", FAR);
            assignAllRoles(b, "TRACK_WORK", FAR);
            confirmAllProtections(a.getId());
            confirmAllProtections(b.getId());
            clock.set(T1.plusSeconds(30));

            service.startWork("BW-A", "ka", "B-WL");
            assertThatThrownBy(() -> service.startWork("BW-B", "kb", "B-WL"))
                    .isInstanceOf(PreconditionNotMetException.class)
                    .satisfies(e -> assertThat(((PreconditionNotMetException) e).getMissingConditions())
                            .extracting(MissingCondition::code).contains("MUTEX_MATRIX_CONFLICT"));
        }

        @Test
        @DisplayName("时间重叠但区段不相交：不构成冲突")
        void overlapDifferentSectionsAllowed() {
            ruleStore.add(new MutexRule("TRACK_WORK", "TRACK_WORK", "自身互斥"));

            BlockWindow a = newCrossMidnightPlan("BW-A2", "TRACK_WORK", List.of("S01"));
            BlockWindow b = newCrossMidnightPlan("BW-B2", "TRACK_WORK", List.of("S04"));
            assignAllRoles(a, "TRACK_WORK", FAR);
            assignAllRoles(b, "TRACK_WORK", FAR);
            confirmAllProtections(a.getId());
            confirmAllProtections(b.getId());
            clock.set(T1.plusSeconds(30));

            service.startWork("BW-A2", "ka", "B-WL");
            assertThat(service.startWork("BW-B2", "kb", "B-WL").started()).isTrue();
        }
    }

    // ------------------------------------------------------------------
    // 场景三：开工确认重试（幂等）
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("开工确认重试")
    class StartRetry {

        @Test
        @DisplayName("相同幂等键重试：重放首次成功结果，不产生第二次开工、第二张回执")
        void retryWithSameIdempotencyKeyReplays() {
            BlockWindow w = newCrossMidnightPlan("BW-IDEM", "CABLE_TEST", List.of("S01"));
            assignAllRoles(w, "CABLE_TEST", FAR);
            confirmAllProtections(w.getId());
            clock.set(T1.plusSeconds(10));

            BlockWindowService.StartOutcome first = service.startWork("BW-IDEM", "same-key", "B-WL");
            assertThat(first.started()).isTrue();
            assertThat(first.replayed()).isFalse();
            Instant startedAt = w.getStartedAt();

            BlockWindowService.StartOutcome retry = service.startWork("BW-IDEM", "same-key", "B-WL");
            assertThat(retry.started()).isFalse();
            assertThat(retry.replayed()).isTrue();
            assertThat(w.getStatus()).isEqualTo(WindowStatus.IN_PROGRESS);
            assertThat(w.getStartedAt()).isEqualTo(startedAt);
            // 只有首次写了一条受理的 START_CONFIRMATION
            long acceptedStarts = confirmationStore.stream()
                    .filter(c -> c.getRecordType().equals("START_CONFIRMATION") && c.isAccepted())
                    .count();
            assertThat(acceptedStarts).isEqualTo(1);
        }

        @Test
        @DisplayName("首次缺条件被拒，补齐后同键重试成功（幂等键用于重放已受理结果，不锁死失败）")
        void retryAfterFixingConditionsSucceeds() {
            BlockWindow w = newCrossMidnightPlan("BW-RETRY", "ROUTINE_INSPECTION", List.of("S01"));
            assignAllRoles(w, "ROUTINE_INSPECTION", FAR);
            // 故意不确认任何保护条件
            clock.set(T1.plusSeconds(10));

            assertThatThrownBy(() -> service.startWork("BW-RETRY", "fix-key", "B-WL"))
                    .isInstanceOf(PreconditionNotMetException.class);

            confirmAllProtections(w.getId());
            BlockWindowService.StartOutcome ok = service.startWork("BW-RETRY", "fix-key", "B-WL");
            assertThat(ok.started()).isTrue();
        }
    }

    // ------------------------------------------------------------------
    // 失败必须说明具体缺失条件
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("逐项缺失条件")
    class DetailedFailures {

        @Test
        @DisplayName("缺岗位、保护未确认、时间未到：一次性返回多项具体条件而非笼统审批失败")
        void multipleSpecificConditionsReturned() {
            BlockWindow w = newCrossMidnightPlan("BW-DETAIL", "CABLE_TEST", List.of("S01", "S02"));
            // 只派一个角色，且完全不确认保护
            assignRole(w, "B-WL", "WORK_LEADER", "CABLE_TEST", FAR);
            clock.set(T1.minusSeconds(300)); // 还没到开窗

            assertThatThrownBy(() -> service.startWork("BW-DETAIL", "k", "B-WL"))
                    .isInstanceOf(PreconditionNotMetException.class)
                    .satisfies(e -> {
                        var codes = ((PreconditionNotMetException) e).getMissingConditions()
                                .stream().map(MissingCondition::code).toList();
                        assertThat(codes).contains(
                                "ROLE_NOT_ASSIGNED",
                                "PROTECTION_NOT_CONFIRMED",
                                "BEFORE_PLANNED_START");
                        // 每个保护项都应被列出（2 区段 × 3 项 = 6）
                        long prot = ((PreconditionNotMetException) e).getMissingConditions().stream()
                                .filter(m -> m.code().equals("PROTECTION_NOT_CONFIRMED")).count();
                        assertThat(prot).isEqualTo(6);
                    });
        }

        @Test
        @DisplayName("不相邻的区段不能拼成一个窗口")
        void nonAdjacentSectionsRejected() {
            assertThatThrownBy(() -> service.createPlan("BW-GAP", "巡视", "ROUTINE_INSPECTION",
                    List.of("S01", "S04"), T1, T2, "ZONE-A"))
                    .isInstanceOf(ApiConflictException.class)
                    .hasMessageContaining("不相邻");
        }
    }

    // ------------------------------------------------------------------
    // 资质在预计结束前失效 → 重新安排
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("资质失效")
    class QualificationExpiry {

        @Test
        @DisplayName("资质在窗口预计结束前失效：派工即被拒，提示重新安排")
        void assignmentBlockedWhenQualificationExpiresBeforeEnd() {
            BlockWindow w = newCrossMidnightPlan("BW-EXP", "SIGNAL_REPLACEMENT", List.of("S02"));
            Personnel soonExpire = addPerson("B-SOON", "WORK_LEADER", "SIGNAL_REPLACEMENT",
                    Instant.parse("2026-09-30T23:00:00Z")); // 早于 T2(次日01:30)

            assertThatThrownBy(() -> service.assign("BW-EXP", "B-SOON", "WORK_LEADER"))
                    .isInstanceOf(ApiConflictException.class)
                    .hasMessageContaining("资质将于")
                    .hasMessageContaining("重新安排");
        }

        @Test
        @DisplayName("派工后资质判定仍在开工闸门复核（失效 → QUALIFICATION_EXPIRES_BEFORE_PLANNED_END）")
        void startGateRechecksQualification() throws Exception {
            BlockWindow w = newCrossMidnightPlan("BW-EXP2", "SIGNAL_REPLACEMENT", List.of("S02"));
            // 直接插入一个“资质恰好等于预计结束时刻”的派工（边界：不晚于结束即失效）
            Personnel edge = addPerson("B-EDGE", "WORK_LEADER", "SIGNAL_REPLACEMENT", T2);
            assignmentRepo.save(new PlanAssignment(w.getId(), edge.getId(), "WORK_LEADER"));
            assignRole(w, "B-SO", "SAFETY_OFFICER", "SIGNAL_REPLACEMENT", FAR);
            assignRole(w, "B-CT", "CONTACT", "SIGNAL_REPLACEMENT", FAR);
            assignRole(w, "B-PT", "PROTECTOR", "SIGNAL_REPLACEMENT", FAR);
            confirmAllProtections(w.getId());
            clock.set(T1.plusSeconds(10));

            assertThatThrownBy(() -> service.startWork("BW-EXP2", "k", "B-WL"))
                    .isInstanceOf(PreconditionNotMetException.class)
                    .satisfies(e -> assertThat(((PreconditionNotMetException) e).getMissingConditions())
                            .extracting(MissingCondition::code)
                            .contains("QUALIFICATION_EXPIRES_BEFORE_PLANNED_END"));
        }

        @Test
        @DisplayName("作业类型不在资质范围内：PERSONNEL_QUALIFICATION_MISMATCH")
        void wrongWorkTypeQualification() {
            BlockWindow w = newCrossMidnightPlan("BW-WT", "OVERHEAD_LINE_WORK", List.of("S02"));
            // 只具备 SIGNAL_REPLACEMENT
            Personnel p = addPerson("B-WL2", "WORK_LEADER", "SIGNAL_REPLACEMENT", FAR);
            assignmentRepo.save(new PlanAssignment(w.getId(), p.getId(), "WORK_LEADER"));
            assignRole(w, "B-SO", "SAFETY_OFFICER", "OVERHEAD_LINE_WORK", FAR);
            assignRole(w, "B-CT", "CONTACT", "OVERHEAD_LINE_WORK", FAR);
            assignRole(w, "B-PT", "PROTECTOR", "OVERHEAD_LINE_WORK", FAR);
            confirmAllProtections(w.getId());
            clock.set(T1.plusSeconds(10));

            assertThatThrownBy(() -> service.startWork("BW-WT", "k", "B-WL"))
                    .isInstanceOf(PreconditionNotMetException.class)
                    .satisfies(e -> assertThat(((PreconditionNotMetException) e).getMissingConditions())
                            .extracting(MissingCondition::code)
                            .contains("PERSONNEL_QUALIFICATION_MISMATCH"));
        }
    }

    // ------------------------------------------------------------------
    // 销记：逐项复核 + 人员撤离
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("销记前置")
    class ReleasePreconditions {

        @Test
        @DisplayName("缺一项保护复核或缺一人撤离：销记被拒并逐项列出")
        void releaseRequiresEveryReviewAndEvacuation() {
            BlockWindow w = newCrossMidnightPlan("BW-REL", "CABLE_TEST", List.of("S02"));
            assignAllRoles(w, "CABLE_TEST", FAR);
            confirmAllProtections(w.getId());
            clock.set(T1.plusSeconds(10));
            service.startWork("BW-REL", "ks", "B-WL");

            // 只复核保护，不撤离
            protectionsReviewAll(w);
            assertThatThrownBy(() -> service.release("BW-REL", "kr", "B-WL"))
                    .isInstanceOf(PreconditionNotMetException.class)
                    .satisfies(e -> assertThat(((PreconditionNotMetException) e).getMissingConditions())
                            .extracting(MissingCondition::code)
                            .containsOnly("PERSONNEL_NOT_EVACUATED"));

            evacuateAll(w);
            // 仍缺全部 REVIEW？不——上面已复核；再人为删掉一个撤离项场景不需要。
            BlockWindowService.ReleaseOutcome out = service.release("BW-REL", "kr", "B-WL");
            assertThat(out.released()).isTrue();
        }

        @Test
        @DisplayName("销记重试同键重放，不重复销记")
        void releaseIdempotentReplay() {
            BlockWindow w = newCrossMidnightPlan("BW-REL2", "CABLE_TEST", List.of("S02"));
            assignAllRoles(w, "CABLE_TEST", FAR);
            confirmAllProtections(w.getId());
            clock.set(T1.plusSeconds(10));
            service.startWork("BW-REL2", "ks", "B-WL");
            protectionsReviewAll(w);
            evacuateAll(w);

            BlockWindowService.ReleaseOutcome r1 = service.release("BW-REL2", "same-rel", "B-WL");
            BlockWindowService.ReleaseOutcome r2 = service.release("BW-REL2", "same-rel", "B-WL");
            assertThat(r1.released()).isTrue();
            assertThat(r2.replayed()).isTrue();
        }
    }

    // ------------------------------------------------------------------
    // 迟到消息不能让已销记计划重新开工
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("迟到开工消息")
    class LateMessages {

        @Test
        @DisplayName("已销记计划收到迟到开工消息：拒绝、留痕、状态保持 RELEASED")
        void lateStartAfterReleaseNeverReopens() {
            BlockWindow w = newCrossMidnightPlan("BW-LATE", "CABLE_TEST", List.of("S02"));
            assignAllRoles(w, "CABLE_TEST", FAR);
            confirmAllProtections(w.getId());
            clock.set(T1.plusSeconds(10));
            service.startWork("BW-LATE", "ks", "B-WL");
            protectionsReviewAll(w);
            evacuateAll(w);
            service.release("BW-LATE", "kr", "B-WL");
            assertThat(w.getStatus()).isEqualTo(WindowStatus.RELEASED);

            // 迟到消息（模拟网络重传，消息标识 MSG-LATE-1）
            boolean accepted = service.acceptLateStartMessage("BW-LATE", "MSG-LATE-1", "B-WL");
            assertThat(accepted).isFalse();
            assertThat(w.getStatus()).isEqualTo(WindowStatus.RELEASED);

            // 即便直接走开工 API 也必须拒绝
            assertThatThrownBy(() -> service.startWork("BW-LATE", "ks", "B-WL"))
                    .isInstanceOf(LateStartRejectedException.class);
            assertThat(w.getStatus()).isEqualTo(WindowStatus.RELEASED);

            // 审计表能找到拒绝记录
            ConfirmationRecord late = confirmationStore.stream()
                    .filter(c -> c.getRecordType().equals("LATE_START_MESSAGE")).findFirst().orElseThrow();
            assertThat(late.isAccepted()).isFalse();
            assertThat(late.getRejectReason()).contains("RELEASED");
        }
    }
}
