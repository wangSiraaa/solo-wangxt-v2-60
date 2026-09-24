package com.railwindow.sim.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 开工确认/开工消息。客户端重试时携带同一个 idempotencyKey，
 * 首次成功的结果会被原样回放，不会重复开工或重复发回执。
 */
public record StartRequest(
        @NotNull Long confirmedByPersonId,
        String idempotencyKey) {
}
