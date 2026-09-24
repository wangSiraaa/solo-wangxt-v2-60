package com.railwindow.sim;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 跨午夜封锁窗口演练：窗口 23:00（当日）~ 02:00（次日），
 * 用绝对时刻判断开放/关闭；窗口结束后的迟到开工消息必须被拒绝。
 */
class CrossMidnightWindowIntegrationTest extends AbstractIntegrationTest {

    private Long createCrossMidnightPlan() throws Exception {
        Map<String, Object> body = Map.of(
                "title", "跨午夜道岔检修",
                "workType", "SIGNAL_REPAIR",
                "windowStart", "2026-09-24T23:00:00Z",
                "windowEnd", "2026-09-25T02:00:00Z",
                "plannedFinish", "2026-09-25T01:30:00Z",
                "sectionCodes", List.of("S02"),
                "personIds", List.of(1, 3));
        return JsonUtils.readId(mockMvc.perform(post("/api/plans")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void startsWithinCrossMidnightWindowAndRejectsLateMessageAfterClose() throws Exception {
        Long id = createCrossMidnightPlan();

        // 12:00Z 排定：模拟调度受理回执落库
        mockMvc.perform(post("/api/plans/" + id + "/schedule")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "windowStart", "2026-09-24T23:00:00Z",
                                "windowEnd", "2026-09-25T02:00:00Z",
                                "plannedFinish", "2026-09-25T01:30:00Z"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receipts[0].simulated").value(true))
                .andExpect(jsonPath("$.receipts[0].receiptNo").value(org.hamcrest.Matchers.startsWith("SIM-RCP-")));

        // 窗口开放前预检：提示窗口未开放
        mockMvc.perform(get("/api/plans/" + id + "/preconditions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startAllowed").value(false))
                .andExpect(jsonPath("$.violations[?(@.code=='WINDOW_NOT_OPEN')]").exists());

        // 保护 + 到岗（在窗口开放前先准备好）
        mockMvc.perform(post("/api/plans/" + id + "/protections")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sectionCode", "S02", "personId", 2, "note", "防护信号牌已设置"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/plans/" + id + "/arrivals")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("personId", 1))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/plans/" + id + "/arrivals")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("personId", 3))))
                .andExpect(status().isOk());

        // 推进到 23:30Z（已进入跨午夜窗口），开工成功
        com.railwindow.sim.testsupport.EmbeddedPgTestConfig.CLOCK.setInstant(
                java.time.Instant.parse("2026-09-24T23:30:00Z"));
        mockMvc.perform(post("/api/plans/" + id + "/start")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "confirmedByPersonId", 3, "idempotencyKey", "cross-midnight-start-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(false))
                .andExpect(jsonPath("$.receipt.kind").value("START"))
                .andExpect(jsonPath("$.receipt.simulated").value(true));

        // 逐项确认销记清单，再销记
        mockMvc.perform(post("/api/plans/" + id + "/closeout-items/SITE_REVIEW")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("confirmedByPersonId", 3))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/plans/" + id + "/closeout-items/PERSONNEL_WITHDRAWAL")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("confirmedByPersonId", 3))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/plans/" + id + "/closeout")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "confirmedByPersonId", 3, "idempotencyKey", "cross-midnight-close-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.status").value("CLOSED"));

        // 迟到的开工消息：已销记，必须 422 且原因是“已销记/迟到消息不得重新开工”，状态保持 CLOSED
        com.railwindow.sim.testsupport.EmbeddedPgTestConfig.CLOCK.setInstant(
                java.time.Instant.parse("2026-09-25T02:30:00Z"));
        mockMvc.perform(post("/api/plans/" + id + "/start")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "confirmedByPersonId", 3, "idempotencyKey", "late-start-attempt"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("CONDITIONS_NOT_MET"))
                .andExpect(jsonPath("$.violations[0].code").value("PLAN_ALREADY_CLOSED"));

        mockMvc.perform(get("/api/plans/" + id))
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.startedAt").exists())
                .andExpect(jsonPath("$.closedAt").exists());
    }

    @Test
    void rejectsStartAfterWindowExpiresEvenIfNotClosed() throws Exception {
        Long id = createCrossMidnightPlan();
        mockMvc.perform(post("/api/plans/" + id + "/schedule")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "windowStart", "2026-09-24T23:00:00Z",
                                "windowEnd", "2026-09-25T02:00:00Z",
                                "plannedFinish", "2026-09-25T01:30:00Z"))))
                .andExpect(status().isOk());

        com.railwindow.sim.testsupport.EmbeddedPgTestConfig.CLOCK.setInstant(
                java.time.Instant.parse("2026-09-25T02:01:00Z"));
        mockMvc.perform(get("/api/plans/" + id + "/preconditions"))
                .andExpect(jsonPath("$.violations[?(@.code=='WINDOW_EXPIRED')]").exists());
    }
}
