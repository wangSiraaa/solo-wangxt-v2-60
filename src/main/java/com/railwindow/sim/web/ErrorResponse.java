package com.railwindow.sim.web;

import java.util.List;

import com.railwindow.sim.service.ConditionViolation;

/**
 * 统一错误响应。条件类错误携带 violations 逐项说明，绝不返回笼统的“审批失败”。
 */
public record ErrorResponse(String error, String message, List<ConditionViolation> violations) {

    public static ErrorResponse of(String error, String message) {
        return new ErrorResponse(error, message, List.of());
    }

    public static ErrorResponse of(String error, String message, List<ConditionViolation> violations) {
        return new ErrorResponse(error, message, violations);
    }
}
