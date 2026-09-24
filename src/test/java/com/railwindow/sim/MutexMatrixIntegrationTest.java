package com.railwindow.sim;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 两个计划竞争同一区段：
 * <ul>
 *   <li>两个计划都允许排定（窗口重叠不直接冲突）；开工时按互斥矩阵对在施(ACTIVE)计划裁决——
 *       先开工者占用，后开工者得到 MUTEX_CONFLICT（含对方计划号、共用区段、矩阵依据），
 *       而不是只比设备编号；</li>
 *   <li>信号检修 × 线路巡检（矩阵明确 exclusive=false）→ 窗口重叠、同区段也可并行开工。</li>
 * </ul>
 */
class MutexMatrixIntegrationTest extends AbstractIntegrationTest {

    private Map<String, Object> planBody(String workType, String start, String end, String finish) {
        return Map.of(
                "title", workType + "演练",
                "workType", workType,
                "windowStart", start,
                "windowEnd", end,
                "plannedFinish", finish,
                "sectionCodes", List.of("S01", "S02"),
                "personIds", List.of(1, 3));
    }

    /** 种子数据中人员3资质 2026-09-25 失效；10/11 月用例前补长期资质。 */
    private void renewPerson3Qualification(String workType) throws Exception {
        mockMvc.perform(post("/api/reference/qualifications")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "personId", 3,
                                "workType", workType,
                                "validFrom", "2026-01-01T00:00:00Z",
                                "validUntil", "2027-12-31T00:00:00Z"))))
                .andExpect(status().isCreated());
    }

    private String createAndSchedule(String workType, String start, String end, String finish) throws Exception {
        String created = mockMvc.perform(post("/api/plans")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(planBody(workType, start, end, finish))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = JsonUtils.readId(created);
        mockMvc.perform(post("/api/plans/" + id + "/schedule")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "windowStart", start, "windowEnd", end, "plannedFinish", finish))))
                .andExpect(status().isOk());
        return objectMapper.readTree(created).get("planNo").asText();
    }

    private void readyForStart(long id) throws Exception {
        for (String code : List.of("S01", "S02")) {
            mockMvc.perform(post("/api/plans/" + id + "/protections")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "sectionCode", code, "personId", 2))))
                    .andExpect(status().isOk());
        }
        for (long personId : List.of(1L, 3L)) {
            mockMvc.perform(post("/api/plans/" + id + "/arrivals")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(Map.of("personId", personId))))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void sameWorkTypeCompetingForSameSectionConflictsByMatrixAtStart() throws Exception {
        renewPerson3Qualification("SIGNAL_REPAIR");
        String start = "2026-10-01T22:00:00Z";
        String end = "2026-10-02T02:00:00Z";
        String finish = "2026-10-02T01:30:00Z";

        String firstNo = createAndSchedule("SIGNAL_REPAIR", start, end, finish);
        long first = Long.parseLong(firstNo.replace("PL-", ""));
        String secondNo = createAndSchedule("SIGNAL_REPAIR",
                "2026-10-01T23:00:00Z", "2026-10-02T03:00:00Z", "2026-10-02T02:30:00Z");
        long second = Long.parseLong(secondNo.replace("PL-", ""));

        com.railwindow.sim.testsupport.EmbeddedPgTestConfig.CLOCK.setInstant(
                java.time.Instant.parse("2026-10-01T23:05:00Z"));

        // 第一个计划开工成功（成为 ACTIVE）
        readyForStart(first);
        mockMvc.perform(post("/api/plans/" + first + "/start")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "confirmedByPersonId", 3, "idempotencyKey", "winner"))))
                .andExpect(status().isOk());

        // 第二个计划：保护与人员同样到位，但矩阵判定与在施计划冲突 → 422 且给出对方计划号、共用区段、矩阵依据
        readyForStart(second);
        mockMvc.perform(post("/api/plans/" + second + "/start")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "confirmedByPersonId", 3, "idempotencyKey", "loser"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[?(@.code=='MUTEX_CONFLICT')]").exists())
                .andExpect(jsonPath("$.violations[0].refs.sharedSections").isArray())
                .andExpect(jsonPath("$.violations[0].refs.otherPlanNo",
                        org.hamcrest.Matchers.matchesRegex("PL-\\d+")))
                .andExpect(jsonPath("$.violations[0].message",
                        org.hamcrest.Matchers.containsString("互斥矩阵")))
                .andExpect(jsonPath("$.violations[0].message",
                        org.hamcrest.Matchers.containsString(firstNo)));
    }

    @Test
    void overlappingPlansAllowedWhenMatrixSaysNonExclusive() throws Exception {
        renewPerson3Qualification("SIGNAL_REPAIR");
        renewPerson3Qualification("LINE_INSPECTION");
        // 人员1补 LINE_INSPECTION 资质，保证资质条件不干扰矩阵结论
        mockMvc.perform(post("/api/reference/qualifications")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "personId", 1,
                                "workType", "LINE_INSPECTION",
                                "validFrom", "2026-01-01T00:00:00Z",
                                "validUntil", "2027-12-31T00:00:00Z"))))
                .andExpect(status().isCreated());

        long repair = Long.parseLong(createAndSchedule("SIGNAL_REPAIR",
                "2026-10-01T22:00:00Z", "2026-10-02T02:00:00Z", "2026-10-02T01:30:00Z").replace("PL-", ""));
        // 巡检与信号检修窗口重叠、共用 S01/S02，矩阵 exclusive=false → 允许排定并开工
        long inspection = Long.parseLong(createAndSchedule("LINE_INSPECTION",
                "2026-10-01T23:00:00Z", "2026-10-02T01:00:00Z", "2026-10-01T23:59:00Z").replace("PL-", ""));

        com.railwindow.sim.testsupport.EmbeddedPgTestConfig.CLOCK.setInstant(
                java.time.Instant.parse("2026-10-01T23:05:00Z"));
        readyForStart(repair);
        readyForStart(inspection);

        mockMvc.perform(post("/api/plans/" + repair + "/start")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "confirmedByPersonId", 3, "idempotencyKey", "repair"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/plans/" + inspection + "/start")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "confirmedByPersonId", 3, "idempotencyKey", "inspect"))))
                .andExpect(status().isOk());
    }

    @Test
    void scheduledOverlapCoexistsAndLoserSeesSpecificConflictCondition() throws Exception {
        renewPerson3Qualification("SIGNAL_REPAIR");
        // 排定时允许窗口重叠共存
        String firstNo = createAndSchedule("SIGNAL_REPAIR",
                "2026-11-01T22:00:00Z", "2026-11-02T02:00:00Z", "2026-11-02T01:30:00Z");
        String secondCreated = mockMvc.perform(post("/api/plans")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(planBody("SIGNAL_REPAIR",
                                "2026-11-01T23:00:00Z", "2026-11-02T03:00:00Z", "2026-11-02T02:30:00Z"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long second = JsonUtils.readId(secondCreated);
        mockMvc.perform(post("/api/plans/" + second + "/schedule")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "windowStart", "2026-11-01T23:00:00Z",
                                "windowEnd", "2026-11-02T03:00:00Z",
                                "plannedFinish", "2026-11-02T02:30:00Z"))))
                .andExpect(status().isOk());

        // 第一个先开工后，第二个预检/开工得到具体的矩阵冲突
        long first = Long.parseLong(firstNo.replace("PL-", ""));
        com.railwindow.sim.testsupport.EmbeddedPgTestConfig.CLOCK.setInstant(
                java.time.Instant.parse("2026-11-01T23:05:00Z"));
        readyForStart(first);
        mockMvc.perform(post("/api/plans/" + first + "/start")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "confirmedByPersonId", 3, "idempotencyKey", "nov-winner"))))
                .andExpect(status().isOk());

        readyForStart(second);
        mockMvc.perform(post("/api/plans/" + second + "/start")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "confirmedByPersonId", 3, "idempotencyKey", "nov-loser"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0].code").value("MUTEX_CONFLICT"))
                .andExpect(jsonPath("$.violations[0].refs.otherPlanNo").value(firstNo));
    }
}
