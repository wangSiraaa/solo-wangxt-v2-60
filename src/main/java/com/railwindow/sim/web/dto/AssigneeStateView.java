package com.railwindow.sim.web.dto;

public record AssigneeStateView(Long personId, String personName, String role, boolean arrived,
                                boolean qualificationCovers, String qualificationNote) {
}
