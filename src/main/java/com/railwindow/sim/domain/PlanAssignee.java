package com.railwindow.sim.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * 计划指派人员（到岗标记）。
 */
@Embeddable
public class PlanAssignee {

    @Column(name = "person_id", nullable = false)
    private Long personId;

    @Column(name = "arrived", nullable = false)
    private boolean arrived;

    protected PlanAssignee() {
    }

    public PlanAssignee(Long personId) {
        this.personId = personId;
    }

    public Long getPersonId() {
        return personId;
    }

    public boolean isArrived() {
        return arrived;
    }

    public void markArrived() {
        this.arrived = true;
    }
}
