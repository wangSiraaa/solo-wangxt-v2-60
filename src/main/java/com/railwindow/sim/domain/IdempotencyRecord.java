package com.railwindow.sim.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 通用幂等记录：同一 (operation, key) 的重复请求回放首次成功结果，
 * 用于开工确认重试、保护确认重传、销记重放等场景。
 */
@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecord {

    @Id
    @Column(name = "operation_key", length = 120)
    private String operationKey;

    /** 计划 ID，便于排查。 */
    @Column(name = "plan_id")
    private Long planId;

    /** 首次成功时的响应体（JSON）。 */
    @Column(name = "response_json", length = 4000, nullable = false)
    private String responseJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(String operationKey, Long planId, String responseJson, Instant createdAt) {
        this.operationKey = operationKey;
        this.planId = planId;
        this.responseJson = responseJson;
        this.createdAt = createdAt;
    }

    public String getOperationKey() {
        return operationKey;
    }

    public Long getPlanId() {
        return planId;
    }

    public String getResponseJson() {
        return responseJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
