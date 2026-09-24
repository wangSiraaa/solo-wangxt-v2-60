package com.railwindow.sim.testsupport;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import com.railwindow.sim.RailWindowSimApplication;

/**
 * 本地手动冒烟启动器：用嵌入式 PostgreSQL 启动完整应用，便于在没有 Docker/root 的机器上
 * 执行 scripts/demo-walkthrough.sh。不属于自动化测试套件。
 *
 * 运行：mvn test-compile exec:java -Dexec.classpathScope=test \
 *        -Dexec.mainClass=com.railwindow.sim.testsupport.LiveDemoLauncher
 */
public final class LiveDemoLauncher {

    private LiveDemoLauncher() {
    }

    public static void main(String[] args) throws Exception {
        EmbeddedPostgres pg = EmbeddedPostgres.builder()
                .setServerConfig("timezone", "UTC")
                .start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                pg.close();
            } catch (Exception ignored) {
                // ignore
            }
        }));
        System.setProperty("spring.datasource.url", pg.getJdbcUrl("postgres", "postgres"));
        System.setProperty("spring.datasource.username", "postgres");
        System.setProperty("spring.datasource.password", "");
        System.setProperty("spring.datasource.driver-class-name", "org.postgresql.Driver");
        System.setProperty("spring.flyway.enabled", "true");
        System.setProperty("spring.jpa.hibernate.ddl-auto", "validate");
        RailWindowSimApplication.main(args);
    }
}
