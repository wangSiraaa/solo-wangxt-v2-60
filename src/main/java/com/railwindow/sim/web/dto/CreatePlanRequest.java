package com.railwindow.sim.web.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * 创建计划请求。时间一律为带时区的 ISO-8601 时刻（如 2026-09-24T22:30:00Z），
 * 跨午夜窗口只需 windowEnd 落在次日。
 */
public record CreatePlanRequest(
        @NotBlank String title,
        @NotBlank String workType,
        @NotNull Instant windowStart,
        @NotNull Instant windowEnd,
        @NotNull Instant plannedFinish,
        @NotEmpty List<@NotBlank String> sectionCodes,
        @NotEmpty List<@NotNull Long> personIds) {
}
