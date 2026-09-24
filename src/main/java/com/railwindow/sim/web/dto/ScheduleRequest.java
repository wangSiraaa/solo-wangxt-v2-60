package com.railwindow.sim.web.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

/**
 * 排定（或重新安排）窗口请求。重新安排用于资质在预计结束前失效等情形。
 */
public record ScheduleRequest(
        @NotNull Instant windowStart,
        @NotNull Instant windowEnd,
        @NotNull Instant plannedFinish) {
}
