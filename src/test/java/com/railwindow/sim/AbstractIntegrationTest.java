package com.railwindow.sim;

import java.time.Instant;

import com.railwindow.sim.testsupport.EmbeddedPgTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 集成测试基类：嵌入式 PostgreSQL + 可注入时钟。每个用例前清理事务数据并重置时钟。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(EmbeddedPgTestConfig.class)
public abstract class AbstractIntegrationTest {

    @Autowired
    protected org.springframework.test.web.servlet.MockMvc mockMvc;

    @Autowired
    protected com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetEnv() {
        // work_plan 被各子表外键引用，级联清理即可；保留种子基础数据
        jdbcTemplate.update("DELETE FROM work_plan");
        EmbeddedPgTestConfig.CLOCK.setInstant(Instant.parse("2026-09-24T12:00:00Z"));
    }
}
