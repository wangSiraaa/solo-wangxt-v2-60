package com.railwindow.sim.testsupport;

import javax.sql.DataSource;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;

/**
 * 测试配置：启动嵌入式 PostgreSQL（随测试进程的本地二进制，不需要 Docker/root），
 * 并以可推进的 {@link MutableClock} 作为注入时钟覆盖生产系统时钟。
 */
@TestConfiguration
public class EmbeddedPgTestConfig {

    public static final MutableClock CLOCK = new MutableClock(Instant.parse("2026-09-24T12:00:00Z"));

    @Bean(destroyMethod = "close")
    @Primary
    public EmbeddedPostgres embeddedPostgres() throws IOException {
        return EmbeddedPostgres.builder()
                .setServerConfig("timezone", "UTC")
                .start();
    }

    @Bean
    @Primary
    public DataSource dataSource(EmbeddedPostgres pg) {
        return pg.getPostgresDatabase();
    }

    @Bean
    @Primary
    public Clock testClock() {
        return CLOCK;
    }
}
