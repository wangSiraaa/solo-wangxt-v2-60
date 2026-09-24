package com.railwindow.sim;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 销记清单逐项确认、缺失条件逐项返回、多区段相邻性校验。
 */
class CloseoutAndConditionsIntegrationTest extends AbstractIntegrationTest {

    private Long readyPlan(String workType, Object personIds) throws Exception {
        Long id = JsonUtils.readId(mockMvc.perform(post("/api/plans")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "销记演练",
                                "workType", workType,
                                "windowStart", "2026-09-24T20:00:00Z",
                                "windowEnd", "2026-09-24T23:00:00Z",
                                "plannedFinish", "2026-09-24T22:30:00Z",
                                "sectionCodes", List.of("S02"),
                                "personIds", personIds))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/plans/" + id + "/schedule")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "windowStart", "2026-09-24T20:00:00Z",
                                "windowEnd", "2026-09-24T23:00:00Z",
                                "plannedFinish", "2026-09-24T22:30:00Z"))))
                .andExpect(status().isOk());
        return id;
    }

    @Test
    void closeoutRequiresEveryItemConfirmedAndThenEmitsReceipt() throws Exception {
        Long id = readyPlan("SIGNAL_REPAIR", List.of(1, 3));
        com.railwindow.sim.testsupport.EmbeddedPgTestConfig.CLOCK.setInstant(
                java.time.Instant.parse("2026-09-24T20:05:00Z"));
        mockMvc.perform(post("/api/plans/" + id + "/protections")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("sectionCode", "S02", "personId", 2))))
                .andExpect(status().isOk());
        for (long p : List.of(1L, 3L)) {
            mockMvc.perform(post("/api/plans/" + id + "/arrivals")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(Map.of("personId", p))))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/plans/" + id + "/start")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("confirmedByPersonId", 3))))
                .andExpect(status().isOk());

        // 未逐项确认前销记：422，明确缺哪些项
        mockMvc.perform(post("/api/plans/" + id + "/closeout")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("confirmedByPersonId", 3))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0].code").value("CLOSEOUT_ITEMS_PENDING"))
                .andExpect(jsonPath("$.violations[0].refs.pendingItems",
                        org.hamcrest.Matchers.containsInAnyOrder("SITE_REVIEW", "PERSONNEL_WITHDRAWAL")));

        // 只确认一项仍不够
        mockMvc.perform(post("/api/plans/" + id + "/closeout-items/SITE_REVIEW")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("confirmedByPersonId", 3))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/plans/" + id + "/closeout")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "confirmedByPersonId", 3, "idempotencyKey", "close-early"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0].refs.pendingItems",
                        org.hamcrest.Matchers.contains("PERSONNEL_WITHDRAWAL")));

        // 两项齐了才销记；重试同键回放
        mockMvc.perform(post("/api/plans/" + id + "/closeout-items/PERSONNEL_WITHDRAWAL")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("confirmedByPersonId", 3))))
                .andExpect(status().isOk());
        String closeBody = objectMapper.writeValueAsString(Map.of(
                "confirmedByPersonId", 3, "idempotencyKey", "close-final"));
        mockMvc.perform(post("/api/plans/" + id + "/closeout")
                        .contentType("application/json").content(closeBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.status").value("CLOSED"))
                .andExpect(jsonPath("$.receipt.kind").value("CLOSEOUT"))
                .andExpect(jsonPath("$.receipt.simulated").value(true));
        mockMvc.perform(post("/api/plans/" + id + "/closeout")
                        .contentType("application/json").content(closeBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true));
    }

    @Test
    void reportsEachMissingConditionSpecifically() throws Exception {
        // 人员2是防护员，没有 SIGNAL_REPAIR 资质 → 同时触发到岗/资质/保护等多项具体条件
        Long id = readyPlan("SIGNAL_REPAIR", List.of(2));
        com.railwindow.sim.testsupport.EmbeddedPgTestConfig.CLOCK.setInstant(
                java.time.Instant.parse("2026-09-24T20:05:00Z"));
        mockMvc.perform(post("/api/plans/" + id + "/start")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("confirmedByPersonId", 2))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[?(@.code=='PROTECTION_NOT_READY')].refs.sectionCodes[0]")
                        .value("S02"))
                .andExpect(jsonPath("$.violations[?(@.code=='PERSONNEL_NOT_ARRIVED')].refs.personIds[0]")
                        .value(2))
                .andExpect(jsonPath("$.violations[?(@.code=='QUALIFICATION_MISSING')]").exists())
                .andExpect(jsonPath("$.error").value("CONDITIONS_NOT_MET"));
    }

    @Test
    void rejectsNonAdjacentSectionsAndAcceptsAdjacentChain() throws Exception {
        // S03 与 S10 不相邻
        mockMvc.perform(post("/api/plans")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "非相邻区段",
                                "workType", "SIGNAL_REPAIR",
                                "windowStart", "2026-09-24T20:00:00Z",
                                "windowEnd", "2026-09-24T23:00:00Z",
                                "plannedFinish", "2026-09-24T22:30:00Z",
                                "sectionCodes", List.of("S03", "S10"),
                                "personIds", List.of(1)))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0].code").value("SECTIONS_NOT_ADJACENT"));

        // S01-S02-S03 相邻链 → 允许创建
        mockMvc.perform(post("/api/plans")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "相邻链",
                                "workType", "SIGNAL_REPAIR",
                                "windowStart", "2026-09-24T20:00:00Z",
                                "windowEnd", "2026-09-24T23:00:00Z",
                                "plannedFinish", "2026-09-24T22:30:00Z",
                                "sectionCodes", List.of("S01", "S02", "S03"),
                                "personIds", List.of(1)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sections.length()").value(3));
    }
}
