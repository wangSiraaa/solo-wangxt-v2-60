package com.railwindow.sim.web.dto;

/**
 * 开工/销记等动作的返回：计划详情 + 本次动作产生的模拟回执；
 * 幂等重放时 replayed=true。
 */
public record ActionResponse(boolean replayed, PlanView plan, ReceiptView receipt) {
}
