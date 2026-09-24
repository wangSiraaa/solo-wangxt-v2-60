package com.railwindow.sim.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 销记前的逐项确认清单（现场复核、人员撤离）。
 */
@Entity
@Table(name = "closeout_item")
public class CloseoutItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_key", length = 32, nullable = false)
    private CloseoutItemKey itemKey;

    @Column(name = "label", length = 128, nullable = false)
    private String label;

    @Column(name = "confirmed", nullable = false)
    private boolean confirmed;

    @Column(name = "confirmed_by_person_id")
    private Long confirmedByPersonId;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    protected CloseoutItem() {
    }

    public CloseoutItem(Long planId, CloseoutItemKey itemKey, String label) {
        this.planId = planId;
        this.itemKey = itemKey;
        this.label = label;
    }

    public void confirm(Long personId, Instant at) {
        this.confirmed = true;
        this.confirmedByPersonId = personId;
        this.confirmedAt = at;
    }

    public Long getId() {
        return id;
    }

    public Long getPlanId() {
        return planId;
    }

    public CloseoutItemKey getItemKey() {
        return itemKey;
    }

    public String getLabel() {
        return label;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public Long getConfirmedByPersonId() {
        return confirmedByPersonId;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }
}
