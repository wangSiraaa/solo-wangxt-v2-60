package com.railwindow.sim.web.dto;

import java.util.List;

import com.railwindow.sim.service.ConditionViolation;

/**
 * 开工前条件评估视图：逐项列出满足/不满足条件，
 * 不满足项给出具体代码、说明和相关区段/人员。
 */
public record PreconditionsView(String planNo,
                                String status,
                                boolean startAllowed,
                                List<ConditionViolation> violations) {
}
