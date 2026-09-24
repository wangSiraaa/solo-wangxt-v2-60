package com.railwindow.sim.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * 互斥作业矩阵条目：作业类型 {@code workTypeA} 与 {@code workTypeB} 在同一区段时间重叠时，
 * 是否互斥（exclusive=true 表示不能并行）。
 *
 * <p>矩阵是冲突判断的<b>唯一权威依据</b>：只比较设备/区段编号不能得出冲突结论。
 * 存储时两种作业类型按字典序规范化（a &lt; b），并对自组合也显式给出条目。
 */
@Entity
@Table(name = "mutex_rule")
@IdClass(MutexRuleId.class)
public class MutexRule {

    @Id
    @Column(name = "work_type_a", length = 48)
    private String workTypeA;

    @Id
    @Column(name = "work_type_b", length = 48)
    private String workTypeB;

    @Column(name = "exclusive", nullable = false)
    private boolean exclusive;

    @Column(name = "remark", length = 200)
    private String remark;

    protected MutexRule() {
    }

    public MutexRule(String workTypeA, String workTypeB, boolean exclusive, String remark) {
        // 规范化，保证 (A,B) 与 (B,A) 是同一行
        if (workTypeA.compareTo(workTypeB) <= 0) {
            this.workTypeA = workTypeA;
            this.workTypeB = workTypeB;
        } else {
            this.workTypeA = workTypeB;
            this.workTypeB = workTypeA;
        }
        this.exclusive = exclusive;
        this.remark = remark;
    }

    public String getWorkTypeA() {
        return workTypeA;
    }

    public String getWorkTypeB() {
        return workTypeB;
    }

    public boolean isExclusive() {
        return exclusive;
    }

    public String getRemark() {
        return remark;
    }
}
