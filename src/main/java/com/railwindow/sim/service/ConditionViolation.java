package com.railwindow.sim.service;

import java.util.Map;

/**
 * 单项缺失/不满足条件的结构化说明。
 *
 * @param code    条件代码
 * @param message 面向计划员的具体说明（含区段、人员等上下文）
 * @param refs    相关引用，如 sectionCodes / personIds / otherPlanNo
 */
public record ConditionViolation(ConditionCode code, String message, Map<String, Object> refs) {

    public static ConditionViolation of(ConditionCode code, String message) {
        return new ConditionViolation(code, message, Map.of());
    }

    public static ConditionViolation of(ConditionCode code, String message, Map<String, Object> refs) {
        return new ConditionViolation(code, message, refs);
    }
}
