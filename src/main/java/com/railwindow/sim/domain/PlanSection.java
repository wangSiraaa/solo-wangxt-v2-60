package com.railwindow.sim.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * 计划占用的区段（允许一个计划占用多个相邻区段）。
 */
@Embeddable
public class PlanSection {

    @Column(name = "section_code", length = 32, nullable = false)
    private String sectionCode;

    /** 该区段的封锁保护是否已确认到位。 */
    @Column(name = "protection_confirmed", nullable = false)
    private boolean protectionConfirmed;

    @Column(name = "protected_by_person_id")
    private Long protectedByPersonId;

    protected PlanSection() {
    }

    public PlanSection(String sectionCode) {
        this.sectionCode = sectionCode;
    }

    public String getSectionCode() {
        return sectionCode;
    }

    public boolean isProtectionConfirmed() {
        return protectionConfirmed;
    }

    public void markProtected(Long personId) {
        this.protectionConfirmed = true;
        this.protectedByPersonId = personId;
    }

    public Long getProtectedByPersonId() {
        return protectedByPersonId;
    }
}
