package com.railwindow.sim.service;

/** 计划状态与请求动作冲突（HTTP 409）。 */
public class IllegalPlanStateException extends RuntimeException {
    public IllegalPlanStateException(String message) {
        super(message);
    }
}
