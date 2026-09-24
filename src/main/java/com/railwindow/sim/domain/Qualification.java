package com.railwindow.sim.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 人员资质：允许从事某类作业（work_type），有效期至 {@code validUntil}（含时刻）。
 */
@Entity
@Table(name = "qualification")
public class Qualification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Column(name = "work_type", length = 48, nullable = false)
    private String workType;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;

    protected Qualification() {
    }

    public Qualification(Person person, String workType, Instant validFrom, Instant validUntil) {
        this.person = person;
        this.workType = workType;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
    }

    /** 资质是否覆盖整个作业时段：开工时有效且不早于预计结束时刻失效。 */
    public boolean covers(Instant from, Instant until) {
        return !validFrom.isAfter(from) && !validUntil.isBefore(until);
    }

    public Long getId() {
        return id;
    }

    public Person getPerson() {
        return person;
    }

    public String getWorkType() {
        return workType;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidUntil() {
        return validUntil;
    }
}
