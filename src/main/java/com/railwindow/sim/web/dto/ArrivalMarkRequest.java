package com.railwindow.sim.web.dto;

import jakarta.validation.constraints.NotNull;

public record ArrivalMarkRequest(@NotNull Long personId) {
}
