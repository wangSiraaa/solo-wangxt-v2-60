package com.railwindow.sim.web.dto;

import jakarta.validation.constraints.NotNull;

public record CloseoutItemConfirmRequest(
        @NotNull Long confirmedByPersonId,
        String idempotencyKey) {
}
