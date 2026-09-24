package com.railwindow.sim.web.dto;

public record SectionStateView(String sectionCode, boolean protectionConfirmed, Long protectedByPersonId) {
}
