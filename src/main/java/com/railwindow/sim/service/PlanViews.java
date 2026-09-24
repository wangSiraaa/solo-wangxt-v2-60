package com.railwindow.sim.service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.railwindow.sim.domain.Person;
import com.railwindow.sim.domain.PlanAssignee;
import com.railwindow.sim.domain.PlanSection;
import com.railwindow.sim.domain.DispatchReceipt;
import com.railwindow.sim.domain.Qualification;
import com.railwindow.sim.domain.WorkPlan;
import com.railwindow.sim.repo.CloseoutItemRepository;
import com.railwindow.sim.repo.DispatchReceiptRepository;
import com.railwindow.sim.repo.PersonRepository;
import com.railwindow.sim.repo.QualificationRepository;
import com.railwindow.sim.web.dto.AssigneeStateView;
import com.railwindow.sim.web.dto.CloseoutItemView;
import com.railwindow.sim.web.dto.PlanView;
import com.railwindow.sim.web.dto.ReceiptView;
import com.railwindow.sim.web.dto.SectionStateView;
import org.springframework.stereotype.Component;

/**
 * 将聚合根组装成对外视图。
 */
@Component
public class PlanViews {

    private final Clock clock;
    private final CloseoutItemRepository closeoutItemRepository;
    private final DispatchReceiptRepository receiptRepository;
    private final PersonRepository personRepository;
    private final QualificationRepository qualificationRepository;

    public PlanViews(Clock clock,
                     CloseoutItemRepository closeoutItemRepository,
                     DispatchReceiptRepository receiptRepository,
                     PersonRepository personRepository,
                     QualificationRepository qualificationRepository) {
        this.clock = clock;
        this.closeoutItemRepository = closeoutItemRepository;
        this.receiptRepository = receiptRepository;
        this.personRepository = personRepository;
        this.qualificationRepository = qualificationRepository;
    }

    public PlanView build(WorkPlan plan) {
        List<SectionStateView> sectionViews = plan.getSections().stream()
                .map(this::section)
                .toList();

        List<Long> personIds = plan.getAssignees().stream().map(PlanAssignee::getPersonId).toList();
        Map<Long, Person> people = new HashMap<>();
        personRepository.findAllById(personIds).forEach(p -> people.put(p.getId(), p));
        List<Qualification> qualifications = personIds.isEmpty()
                ? List.of()
                : qualificationRepository.findByPersonIdIn(personIds);
        Instant now = clock.instant();

        List<AssigneeStateView> assigneeViews = plan.getAssignees().stream()
                .map(a -> assignee(a, plan, people, qualifications, now))
                .toList();

        List<CloseoutItemView> itemViews = closeoutItemRepository.findByPlanIdOrderById(plan.getId()).stream()
                .map(i -> new CloseoutItemView(i.getItemKey().name(), i.getLabel(), i.isConfirmed(),
                        i.getConfirmedByPersonId(), i.getConfirmedAt()))
                .toList();

        List<ReceiptView> receiptViews = receiptRepository.findByPlanIdOrderById(plan.getId()).stream()
                .map(PlanViews::receipt)
                .toList();

        return new PlanView(plan.getId(), plan.getPlanNo(), plan.getTitle(), plan.getWorkType(),
                plan.getStatus().name(), plan.getWindowStart(), plan.getWindowEnd(), plan.getPlannedFinish(),
                plan.getStartedAt(), plan.getClosedAt(), plan.getRelatedPlanId(),
                sectionViews, assigneeViews, itemViews, receiptViews);
    }

    private SectionStateView section(PlanSection s) {
        return new SectionStateView(s.getSectionCode(), s.isProtectionConfirmed(), s.getProtectedByPersonId());
    }

    private AssigneeStateView assignee(PlanAssignee a, WorkPlan plan,
                                       Map<Long, Person> people,
                                       List<Qualification> qualifications,
                                       Instant now) {
        Person p = people.get(a.getPersonId());
        String name = p == null ? "人员#" + a.getPersonId() : p.getName();
        String role = p == null ? "未知" : p.getRole();
        List<Qualification> mine = qualifications.stream()
                .filter(q -> q.getPerson().getId().equals(a.getPersonId()))
                .filter(q -> q.getWorkType().equalsIgnoreCase(plan.getWorkType()))
                .toList();
        Qualification best = mine.stream()
                .filter(q -> q.covers(now, plan.getPlannedFinish()))
                .findFirst()
                .orElse(mine.stream().findFirst().orElse(null));
        boolean covers = best != null && best.covers(now, plan.getPlannedFinish());
        String note;
        if (mine.isEmpty()) {
            note = "缺少作业类型[" + plan.getWorkType() + "]资质";
        } else if (!covers) {
            note = "资质有效期 " + best.getValidFrom() + " ~ " + best.getValidUntil()
                    + "，未覆盖到预计结束 " + plan.getPlannedFinish();
        } else {
            note = "资质有效期至 " + best.getValidUntil();
        }
        return new AssigneeStateView(a.getPersonId(), name, role, a.isArrived(), covers, note);
    }

    public static ReceiptView receipt(DispatchReceipt r) {
        return new ReceiptView(r.getReceiptNo(), r.getKind().name(), r.getStatus(),
                r.getPayload(), r.getCreatedAt(), true);
    }
}
