package com.railwindow.sim.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 铁路区段（模拟）。注意：区段编号只是标识，不能作为作业是否互斥的判断依据。
 */
@Entity
@Table(name = "section")
public class Section {

    @Id
    @Column(name = "code", length = 32)
    private String code;

    @Column(name = "name", length = 128, nullable = false)
    private String name;

    @Column(name = "adjacent_to", length = 32)
    private String adjacentTo;

    protected Section() {
    }

    public Section(String code, String name, String adjacentTo) {
        this.code = code;
        this.name = name;
        this.adjacentTo = adjacentTo;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getAdjacentTo() {
        return adjacentTo;
    }
}
