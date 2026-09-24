package com.railwindow.sim;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 资质在预计结束前失效：预检必须给出 QUALIFICATION_EXPIRES_BEFORE_FINISH；
 * 用 /reschedule 重新安排到资质有效期内的窗口，原计划作废、接替计划可开工。
 */
class QualificationExpiryRescheduleIntegrationTest extends AbstractIntegrationTest {

    private Map<String, Object> body(String start, String end, String finish) {
        return Map.of(
                "title", "资质临界演练",
                "workType", "SIGNAL_REPAIR",
                "windowStart", start,
                "windowEnd", end,
                "plannedFinish", finish,
                "sectionCodes", List.of("S03"),
                "personIds", List.of(3)); // 人员3 资质 2026-09-25T23:00Z 失效（种子数据）
    }

    @Test
    void blocksStartWhenQualificationExpiresBeforePlannedFinishAndRescheduleResolves() throws Exception {
        Long id = JsonUtils.readId(mockMvc.perform(post("/api/plans")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                body("2026-09-25T20:00:00Z", "2026-09-26T02:00:00Z",
                                        "2026-09-25T23:30:00Z"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        com.railwindow.sim.testsupport.EmbeddedPgTestConfig.CLOCK.setInstant(
                java.time.Instant.parse("2026-09-25T20:00:00Z"));

        mockMvc.perform(post("/api/plans/" + id + "/schedule")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "windowStart", "2026-09-25T20:00:00Z",
                                "windowEnd", "2026-09-26T02:00:00Z",
                                "plannedFinish", "2026-09-25T23:30:00Z"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/plans/" + id + "/protections")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("sectionCode", "S03", "personId", 2))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/plans/" + id + "/arrivals")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("personId", 3))))
                .andExpect(status().isOk());

        // 预计结束 23:30 晚于资质失效 23:00 → 具体条件提示重新安排
        mockMvc.perform(post("/api/plans/" + id + "/start")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("confirmedByPersonId", 3))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[?(@.code=='QUALIFICATION_EXPIRES_BEFORE_FINISH')]").exists())
                .andExpect(jsonPath("$.violations[0].refs.validUntil").value("2026-09-25T23:00:00Z"))
                .andExpect(jsonPath("$.violations[0].refs.plannedFinish").value("2026-09-25T23:30:00Z"));

        // 重新安排到资质有效期内的窗口
        Long successorId = JsonUtils.readId(mockMvc.perform(post("/api/plans/" + id + "/reschedule")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "windowStart", "2026-09-25T18:00:00Z",
                                "windowEnd", "2026-09-25T22:30:00Z",
                                "plannedFinish", "2026-09-25T22:00:00Z"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andReturn().getResponse().getContentAsString());

        // 原计划作废，不得开工
        mockMvc.perform(post("/api/plans/" + id + "/start")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("confirmedByPersonId", 3))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0].code").value("PLAN_SUPERSEDED"));

        // 接替计划需要重新确认保护/到岗，然后可以开工
        mockMvc.perform(get("/api/plans/" + successorId))
                .andExpect(jsonPath("$.sections[0].protectionConfirmed").value(false));
        mockMvc.perform(post("/api/plans/" + successorId + "/protections")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("sectionCode", "S03", "personId", 2))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/plans/" + successorId + "/arrivals")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("personId", 3))))
                .andExpect(status().isOk());

        com.railwindow.sim.testsupport.EmbeddedPgTestConfig.CLOCK.setInstant(
                java.time.Instant.parse("2026-09-25T18:05:00Z"));
        mockMvc.perform(post("/api/plans/" + successorId + "/start")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("confirmedByPersonId", 3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.status").value("ACTIVE"));
    }
}
