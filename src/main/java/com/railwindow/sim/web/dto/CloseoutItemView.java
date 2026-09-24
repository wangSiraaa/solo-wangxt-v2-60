package com.railwindow.sim.web.dto;

import java.time.Instant;

public record CloseoutItemView(String key, String label, boolean confirmed,
                               Long confirmedByPersonId, Instant confirmedAt) {
}
