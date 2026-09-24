package local.train.blockwindow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * 确认审计记录：开工确认（含重试）、迟到消息、销记逐项确认均落本表。
 *
 * <p>{@code accepted=false} 的迟到开工消息是演练重点：销记后到达的确认只能被记录为拒绝，
 * 不允许把计划拉回 IN_PROGRESS。
 */
@Entity
@Table(name = "confirmation_record")
public class ConfirmationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "block_window_id", nullable = false)
    private Long blockWindowId;

    /** START_CONFIRMATION / LATE_START_MESSAGE / RELEASE_CHECK / RELEASE */
    @Column(name = "record_type", nullable = false)
    private String recordType;

    @Column(name = "idem_key")
    private String idemKey;

    @Column(name = "item_key")
    private String itemKey;

    @Column(name = "section_code")
    private String sectionCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "accepted", nullable = false)
    private boolean accepted = true;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected ConfirmationRecord() {
    }

    public ConfirmationRecord(Long blockWindowId, String recordType, String idemKey, String itemKey,
                              String sectionCode, Map<String, Object> payload,
                              boolean accepted, String rejectReason, Instant recordedAt) {
        this.blockWindowId = blockWindowId;
        this.recordType = recordType;
        this.idemKey = idemKey;
        this.itemKey = itemKey;
        this.sectionCode = sectionCode;
        this.payload = payload;
        this.accepted = accepted;
        this.rejectReason = rejectReason;
        this.recordedAt = recordedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getBlockWindowId() {
        return blockWindowId;
    }

    public String getRecordType() {
        return recordType;
    }

    public String getIdemKey() {
        return idemKey;
    }

    public String getItemKey() {
        return itemKey;
    }

    public String getSectionCode() {
        return sectionCode;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
