package com.railwindow.sim.web.dto;

import jakarta.validation.constraints.NotBlank;

public record PersonRequest(@NotBlank String name, @NotBlank String role) {
}
