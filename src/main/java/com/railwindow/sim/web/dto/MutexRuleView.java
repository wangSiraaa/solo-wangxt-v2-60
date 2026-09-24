package com.railwindow.sim.web.dto;

public record MutexRuleView(String workTypeA, String workTypeB, boolean exclusive, String remark) {
}
