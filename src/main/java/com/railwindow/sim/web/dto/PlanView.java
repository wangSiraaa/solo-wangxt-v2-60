package com.railwindow.sim.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * 计划详情视图。
 */
public record PlanView(Long id,
                       String planNo,
                       String title,
                       String workType,
                       String status,
                       Instant windowStart,
                       Instant windowEnd,
                       Instant plannedFinish,
                       Instant startedAt,
                       Instant closedAt,
                       Long replacedByPlanId,
                       List<SectionStateView> sections,
                       List<AssigneeStateView> assignees,
                       List<CloseoutItemView> closeoutItems,
                       List<ReceiptView> receipts) {
}
