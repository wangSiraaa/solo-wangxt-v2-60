package com.railwindow.sim.web.dto;

import java.time.Instant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record QualificationRequest(
        @NotNull Long personId,
        @NotBlank String workType,
        @NotNull Instant validFrom,
        @NotNull Instant validUntil) {
}
