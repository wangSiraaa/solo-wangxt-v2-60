package com.railwindow.sim;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 开工确认重试：网络重传时携带同一 idempotencyKey，
 * 服务端必须回放首次成功结果（replayed=true），且只产生一份开工回执、一条开工确认记录。
 */
class StartRetryIdempotencyIntegrationTest extends AbstractIntegrationTest {

    @Test
    void repeatedStartWithSameIdempotencyKeyReplaysSingleReceipt() throws Exception {
        Long id = JsonUtils.readId(mockMvc.perform(post("/api/plans")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "开工重试验练",
                                "workType", "SIGNAL_REPAIR",
                                "windowStart", "2026-09-24T20:00:00Z",
                                "windowEnd", "2026-09-24T23:00:00Z",
                                "plannedFinish", "2026-09-24T22:30:00Z",
                                "sectionCodes", List.of("S03"),
                                "personIds", List.of(1, 3)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(post("/api/plans/" + id + "/schedule")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "windowStart", "2026-09-24T20:00:00Z",
                                "windowEnd", "2026-09-24T23:00:00Z",
                                "plannedFinish", "2026-09-24T22:30:00Z"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/plans/" + id + "/protections")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("sectionCode", "S03", "personId", 2))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/plans/" + id + "/arrivals")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("personId", 1))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/plans/" + id + "/arrivals")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("personId", 3))))
                .andExpect(status().isOk());

        com.railwindow.sim.testsupport.EmbeddedPgTestConfig.CLOCK.setInstant(
                java.time.Instant.parse("2026-09-24T20:05:00Z"));

        String body = objectMapper.writeValueAsString(Map.of(
                "confirmedByPersonId", 3, "idempotencyKey", "start-retry-001"));

        // 首次：成功，产生 START 回执
        mockMvc.perform(post("/api/plans/" + id + "/start")
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(false))
                .andExpect(jsonPath("$.receipt.kind").value("START"));

        // 第二次（模拟客户端超时重试）：回放，receipt 编号一致
        mockMvc.perform(post("/api/plans/" + id + "/start")
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true))
                .andExpect(jsonPath("$.receipt.kind").value("START"));

        // 第三次：仍然回放
        mockMvc.perform(post("/api/plans/" + id + "/start")
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true));

        // 回执总数 = SCHEDULE + START 各一份；不会因重试产生多份 START 回执
        String detail = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/plans/" + id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(detail);
        long startReceipts = node.get("receipts").findValuesAsText("kind").stream()
                .filter("START"::equals).count();
        org.junit.jupiter.api.Assertions.assertEquals(1, startReceipts);
        org.junit.jupiter.api.Assertions.assertEquals("ACTIVE", node.get("status").asText());
    }

    @Test
    void retryOfFailedStartDoesNotCreateIdempotencyRecordAndCanSucceedLater() throws Exception {
        Long id = JsonUtils.readId(mockMvc.perform(post("/api/plans")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "先失败后重试",
                                "workType", "SIGNAL_REPAIR",
                                "windowStart", "2026-09-24T20:00:00Z",
                                "windowEnd", "2026-09-24T23:00:00Z",
                                "plannedFinish", "2026-09-24T22:30:00Z",
                                "sectionCodes", List.of("S03"),
                                "personIds", List.of(1, 3)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/plans/" + id + "/schedule")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "windowStart", "2026-09-24T20:00:00Z",
                                "windowEnd", "2026-09-24T23:00:00Z",
                                "plannedFinish", "2026-09-24T22:30:00Z"))))
                .andExpect(status().isOk());

        // 什么都没确认：失败，且必须逐项说明缺失条件
        com.railwindow.sim.testsupport.EmbeddedPgTestConfig.CLOCK.setInstant(
                java.time.Instant.parse("2026-09-24T20:05:00Z"));
        mockMvc.perform(post("/api/plans/" + id + "/start")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "confirmedByPersonId", 3, "idempotencyKey", "will-retry"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[?(@.code=='PROTECTION_NOT_READY')]").exists())
                .andExpect(jsonPath("$.violations[?(@.code=='PERSONNEL_NOT_ARRIVED')]").exists());

        // 补齐条件后同键重试：这次真正成功（不是回放失败）
        mockMvc.perform(post("/api/plans/" + id + "/protections")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("sectionCode", "S03", "personId", 2))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/plans/" + id + "/arrivals")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("personId", 1))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/plans/" + id + "/arrivals")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("personId", 3))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/plans/" + id + "/start")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "confirmedByPersonId", 3, "idempotencyKey", "will-retry"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(false));
    }
}
