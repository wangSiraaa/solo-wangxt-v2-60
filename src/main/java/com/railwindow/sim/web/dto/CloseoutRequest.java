package com.railwindow.sim.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 销记请求。所有清单项逐项确认通过后才允许销记。
 */
public record CloseoutRequest(
        @NotNull Long confirmedByPersonId,
        String idempotencyKey) {
}
