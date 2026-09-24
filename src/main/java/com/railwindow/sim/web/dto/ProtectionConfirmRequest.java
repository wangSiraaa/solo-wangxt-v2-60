package com.railwindow.sim.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProtectionConfirmRequest(
        @NotBlank String sectionCode,
        @NotNull Long personId,
        String note) {
}
