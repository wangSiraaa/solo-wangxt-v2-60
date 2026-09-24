package com.railwindow.sim.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 信号检修封锁窗口计划（模拟）。
 */
@Entity
@Table(name = "work_plan")
public class WorkPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 计划编号，业务生成，如 PL-000042。 */
    @Column(name = "plan_no", length = 32, nullable = false, unique = true)
    private String planNo;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    /** 作业类型，参与互斥矩阵判断。 */
    @Column(name = "work_type", length = 48, nullable = false)
    private String workType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private PlanStatus status = PlanStatus.DRAFT;

    /** 封锁窗口开始时刻（UTC）。 */
    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    /** 封锁窗口结束时刻（UTC），允许跨午夜。 */
    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;

    /** 现场作业预计结束时刻，不得晚于窗口结束。 */
    @Column(name = "planned_finish", nullable = false)
    private Instant plannedFinish;

    /** 实际开工时刻。 */
    @Column(name = "started_at")
    private Instant startedAt;

    /** 实际销记时刻。 */
    @Column(name = "closed_at")
    private Instant closedAt;

    /** 重新安排后指向接替计划；或由接替计划指向作废的原计划。 */
    @Column(name = "related_plan_id")
    private Long relatedPlanId;

    /** 开工许可回执编号（幂等回放用）。 */
    @Column(name = "start_receipt_no", length = 40)
    private String startReceiptNo;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "plan_section", joinColumns = @JoinColumn(name = "plan_id"))
    @OrderColumn(name = "position")
    private List<PlanSection> sections = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "plan_assignee", joinColumns = @JoinColumn(name = "plan_id"))
    @OrderColumn(name = "position")
    private List<PlanAssignee> assignees = new ArrayList<>();

    @Version
    @Column(name = "version")
    private Long version;

    protected WorkPlan() {
    }

    public WorkPlan(String planNo, String title, String workType,
                    Instant windowStart, Instant windowEnd, Instant plannedFinish,
                    List<String> sectionCodes, List<Long> personIds) {
        this.planNo = planNo;
        this.title = title;
        this.workType = workType;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.plannedFinish = plannedFinish;
        sectionCodes.forEach(c -> this.sections.add(new PlanSection(c)));
        personIds.forEach(p -> this.assignees.add(new PlanAssignee(p)));
    }

    public boolean isTerminal() {
        return status == PlanStatus.CLOSED || status == PlanStatus.RESCHEDULED;
    }

    public Set<String> sectionCodeSet() {
        Set<String> codes = new LinkedHashSet<>();
        sections.forEach(s -> codes.add(s.getSectionCode()));
        return codes;
    }

    public Long getId() {
        return id;
    }

    public String getPlanNo() {
        return planNo;
    }

    public String getTitle() {
        return title;
    }

    public String getWorkType() {
        return workType;
    }

    public PlanStatus getStatus() {
        return status;
    }

    public void setStatus(PlanStatus status) {
        this.status = status;
    }

    public Instant getWindowStart() {
        return windowStart;
    }

    public Instant getWindowEnd() {
        return windowEnd;
    }

    public Instant getPlannedFinish() {
        return plannedFinish;
    }

    public void setWindow(Instant start, Instant end, Instant finish) {
        this.windowStart = start;
        this.windowEnd = end;
        this.plannedFinish = finish;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void markStarted(Instant at, String receiptNo) {
        this.status = PlanStatus.ACTIVE;
        this.startedAt = at;
        this.startReceiptNo = receiptNo;
    }

    public String getStartReceiptNo() {
        return startReceiptNo;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void markClosed(Instant at) {
        this.status = PlanStatus.CLOSED;
        this.closedAt = at;
    }

    public Long getRelatedPlanId() {
        return relatedPlanId;
    }

    public void setRelatedPlanId(Long relatedPlanId) {
        this.relatedPlanId = relatedPlanId;
    }

    public List<PlanSection> getSections() {
        return sections;
    }

    public List<PlanAssignee> getAssignees() {
        return assignees;
    }

    public Long getVersion() {
        return version;
    }
}
