package com.railwindow.sim.web.dto;

import java.time.Instant;

/**
 * <b>模拟</b>调度回执视图。明确标注 simulated=true。
 */
public record ReceiptView(String receiptNo,
                          String kind,
                          String status,
                          String payload,
                          Instant createdAt,
                          boolean simulated) {
}
