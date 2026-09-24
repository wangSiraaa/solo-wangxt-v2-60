package com.railwindow.sim.web.dto;

import jakarta.validation.constraints.NotBlank;

public record SectionRequest(@NotBlank String code, @NotBlank String name, String adjacentTo) {
}
