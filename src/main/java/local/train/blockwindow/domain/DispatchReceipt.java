package local.train.blockwindow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 模拟调度回执。本地演练用：只生成文本回执，不外发、不联动真实调度台。
 */
@Entity
@Table(name = "dispatch_receipt")
public class DispatchReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receipt_no", nullable = false, unique = true)
    private String receiptNo;

    @Column(name = "block_window_id", nullable = false)
    private Long blockWindowId;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    /** DISPATCHED / CLOSED，模拟调度台受理结果。 */
    @Column(name = "dispatch_status", nullable = false)
    private String dispatchStatus;

    @Column(name = "display_summary", nullable = false, length = 2000)
    private String displaySummary;

    @Column(name = "simulated", nullable = false)
    private boolean simulated = true;

    @Column(name = "note", nullable = false)
    private String note;

    protected DispatchReceipt() {
    }

    public DispatchReceipt(String receiptNo, Long blockWindowId, Instant issuedAt,
                           String dispatchStatus, String displaySummary, String note) {
        this.receiptNo = receiptNo;
        this.blockWindowId = blockWindowId;
        this.issuedAt = issuedAt;
        this.dispatchStatus = dispatchStatus;
        this.displaySummary = displaySummary;
        this.note = note;
    }

    public String getReceiptNo() {
        return receiptNo;
    }

    public Long getBlockWindowId() {
        return blockWindowId;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public String getDispatchStatus() {
        return dispatchStatus;
    }

    public String getDisplaySummary() {
        return displaySummary;
    }

    public boolean isSimulated() {
        return simulated;
    }

    public String getNote() {
        return note;
    }
}
