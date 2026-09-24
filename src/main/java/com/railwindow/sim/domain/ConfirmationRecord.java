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
 * 确认链记录：保护确认、开工确认、销记项确认、销记动作均落库留痕。
 */
@Entity
@Table(name = "confirmation_record")
public class ConfirmationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 24, nullable = false)
    private ConfirmationKind kind;

    /** 细项标识，如区段编号或销记清单项 key。 */
    @Column(name = "ref_key", length = 64)
    private String refKey;

    @Column(name = "person_id")
    private Long personId;

    @Column(name = "message", length = 256)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** 幂等键（客户端重传同一确认时去重）。 */
    @Column(name = "idempotency_key", length = 80)
    private String idempotencyKey;

    protected ConfirmationRecord() {
    }

    public ConfirmationRecord(Long planId, ConfirmationKind kind, String refKey,
                              Long personId, String message, Instant createdAt,
                              String idempotencyKey) {
        this.planId = planId;
        this.kind = kind;
        this.refKey = refKey;
        this.personId = personId;
        this.message = message;
        this.createdAt = createdAt;
        this.idempotencyKey = idempotencyKey;
    }

    public Long getId() {
        return id;
    }

    public Long getPlanId() {
        return planId;
    }

    public ConfirmationKind getKind() {
        return kind;
    }

    public String getRefKey() {
        return refKey;
    }

    public Long getPersonId() {
        return personId;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
