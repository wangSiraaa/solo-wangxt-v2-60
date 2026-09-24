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
 * <b>模拟</b>调度回执。由本地模拟器产生，不与任何真实调度系统通信。
 */
@Entity
@Table(name = "dispatch_receipt")
public class DispatchReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 回执编号，如 SIM-RCP-000123。 */
    @Column(name = "receipt_no", length = 40, nullable = false, unique = true)
    private String receiptNo;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 16, nullable = false)
    private ReceiptKind kind;

    @Column(name = "status", length = 24, nullable = false)
    private String status;

    @Column(name = "payload", length = 2000, nullable = false)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DispatchReceipt() {
    }

    public DispatchReceipt(String receiptNo, Long planId, ReceiptKind kind,
                           String status, String payload, Instant createdAt) {
        this.receiptNo = receiptNo;
        this.planId = planId;
        this.kind = kind;
        this.status = status;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getReceiptNo() {
        return receiptNo;
    }

    public Long getPlanId() {
        return planId;
    }

    public ReceiptKind getKind() {
        return kind;
    }

    public String getStatus() {
        return status;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
