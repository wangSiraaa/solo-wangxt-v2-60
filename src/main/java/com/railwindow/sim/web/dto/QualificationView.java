package com.railwindow.sim.web.dto;

import java.time.Instant;

public record QualificationView(Long id, Long personId, String workType,
                                Instant validFrom, Instant validUntil) {
}
