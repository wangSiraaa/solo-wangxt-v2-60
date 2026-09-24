package local.train.blockwindow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** 区段保护条件（如：停车信号已设、红旗已插、邻线防护已到位）。逐项确认。 */
@Entity
@Table(name = "protection_requirement")
public class ProtectionRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "block_window_id", nullable = false)
    private Long blockWindowId;

    @Column(name = "section_code", nullable = false)
    private String sectionCode;

    @Column(name = "requirement_code", nullable = false)
    private String requirementCode;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "confirmed", nullable = false)
    private boolean confirmed = false;

    @Column(name = "confirmed_by")
    private String confirmedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    protected ProtectionRequirement() {
    }

    public ProtectionRequirement(Long blockWindowId, String sectionCode,
                                 String requirementCode, String label) {
        this.blockWindowId = blockWindowId;
        this.sectionCode = sectionCode;
        this.requirementCode = requirementCode;
        this.label = label;
    }

    public void confirm(String by, Instant at) {
        this.confirmed = true;
        this.confirmedBy = by;
        this.confirmedAt = at;
    }

    public Long getId() {
        return id;
    }

    public Long getBlockWindowId() {
        return blockWindowId;
    }

    public String getSectionCode() {
        return sectionCode;
    }

    public String getRequirementCode() {
        return requirementCode;
    }

    public String getLabel() {
        return label;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public String getConfirmedBy() {
        return confirmedBy;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }
}
