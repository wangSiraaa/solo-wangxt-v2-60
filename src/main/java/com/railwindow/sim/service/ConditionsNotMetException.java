package com.railwindow.sim.service;

import java.util.List;

/**
 * 存在一项或多项不满足条件时抛出（HTTP 422），携带逐项说明。
 */
public class ConditionsNotMetException extends RuntimeException {

    private final transient List<ConditionViolation> violations;

    public ConditionsNotMetException(List<ConditionViolation> violations) {
        super(violations.isEmpty()
                ? "条件不满足"
                : violations.get(0).code().defaultMessage() + " 等 " + violations.size() + " 项条件不满足");
        this.violations = List.copyOf(violations);
    }

    public List<ConditionViolation> getViolations() {
        return violations;
    }
}
