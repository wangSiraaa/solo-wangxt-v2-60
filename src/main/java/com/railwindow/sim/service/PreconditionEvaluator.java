package com.railwindow.sim.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.railwindow.sim.domain.Person;
import com.railwindow.sim.domain.PlanAssignee;
import com.railwindow.sim.domain.PlanSection;
import com.railwindow.sim.domain.PlanStatus;
import com.railwindow.sim.domain.Qualification;
import com.railwindow.sim.domain.WorkPlan;
import com.railwindow.sim.repo.PersonRepository;
import com.railwindow.sim.repo.QualificationRepository;
import com.railwindow.sim.repo.WorkPlanRepository;
import org.springframework.stereotype.Component;

/**
 * 开工前条件逐项评估：窗口时间、区段保护、人员到岗、资质覆盖、互斥矩阵冲突。
 * 输出结构化的逐项缺失条件，供 422 响应与 GET 预检接口共用。
 */
@Component
public class PreconditionEvaluator {

    private final Clock clock;
    private final WorkPlanRepository workPlanRepository;
    private final PersonRepository personRepository;
    private final QualificationRepository qualificationRepository;
    private final MutexService mutexService;

    public PreconditionEvaluator(Clock clock,
                                 WorkPlanRepository workPlanRepository,
                                 PersonRepository personRepository,
                                 QualificationRepository qualificationRepository,
                                 MutexService mutexService) {
        this.clock = clock;
        this.workPlanRepository = workPlanRepository;
        this.personRepository = personRepository;
        this.qualificationRepository = qualificationRepository;
        this.mutexService = mutexService;
    }

    /**
     * @return 不满足条件列表；空列表表示允许开工
     */
    public List<ConditionViolation> evaluate(WorkPlan plan) {
        Instant now = clock.instant();
        List<ConditionViolation> violations = new ArrayList<>();

        // 1) 生命周期状态：终态计划绝不能因迟到消息复活
        switch (plan.getStatus()) {
            case CLOSED -> {
                violations.add(ConditionViolation.of(ConditionCode.PLAN_ALREADY_CLOSED,
                        "计划 " + plan.getPlanNo() + " 已于 " + plan.getClosedAt()
                                + " 销记，迟到的开工消息不得使其重新开工",
                        Map.of("closedAt", plan.getClosedAt())));
                return violations;
            }
            case RESCHEDULED -> {
                violations.add(ConditionViolation.of(ConditionCode.PLAN_SUPERSEDED,
                        "计划 " + plan.getPlanNo() + " 已重新安排并由接替计划生效，原计划不得开工",
                        Map.of("relatedPlanId", plan.getRelatedPlanId() == null ? "" : plan.getRelatedPlanId())));
                return violations;
            }
            case ACTIVE -> {
                violations.add(ConditionViolation.of(ConditionCode.PLAN_ALREADY_ACTIVE,
                        "计划 " + plan.getPlanNo() + " 已在 " + plan.getStartedAt() + " 开工"));
                return violations;
            }
            case DRAFT -> {
                violations.add(ConditionViolation.of(ConditionCode.NOT_SCHEDULED,
                        "计划 " + plan.getPlanNo() + " 尚未排定封锁窗口"));
                return violations;
            }
            case SCHEDULED -> {
                // 继续检查下面的各项条件
            }
        }

        // 2) 窗口时间（支持跨午夜，比较的是绝对时刻而非时钟字面值）
        if (now.isBefore(plan.getWindowStart())) {
            violations.add(ConditionViolation.of(ConditionCode.WINDOW_NOT_OPEN,
                    "封锁窗口尚未开放：当前 " + now + "，窗口开始 " + plan.getWindowStart(),
                    Map.of("now", now, "windowStart", plan.getWindowStart())));
        } else if (!now.isBefore(plan.getWindowEnd())) {
            violations.add(ConditionViolation.of(ConditionCode.WINDOW_EXPIRED,
                    "封锁窗口已于 " + plan.getWindowEnd() + " 关闭，开工消息迟到，禁止开工",
                    Map.of("now", now, "windowEnd", plan.getWindowEnd())));
        }

        // 3) 区段保护逐项确认
        List<String> unprotected = plan.getSections().stream()
                .filter(s -> !s.isProtectionConfirmed())
                .map(PlanSection::getSectionCode)
                .toList();
        if (!unprotected.isEmpty()) {
            violations.add(ConditionViolation.of(ConditionCode.PROTECTION_NOT_READY,
                    "以下区段尚未确认封锁保护到位：" + unprotected,
                    Map.of("sectionCodes", unprotected)));
        }

        // 4) 人员到岗
        List<Long> absent = plan.getAssignees().stream()
                .filter(a -> !a.isArrived())
                .map(PlanAssignee::getPersonId)
                .toList();
        if (!absent.isEmpty()) {
            violations.add(ConditionViolation.of(ConditionCode.PERSONNEL_NOT_ARRIVED,
                    "以下指派人员尚未到岗：" + absent,
                    Map.of("personIds", absent)));
        }

        // 5) 资质：作业类型匹配 + 覆盖到预计结束时刻
        evaluateQualifications(plan, now, violations);

        // 6) 互斥作业矩阵冲突：只与已开工(ACTIVE)的计划逐对判断——
        //    多个计划可以同时处于 SCHEDULED，竞争在开工时刻裁决，避免互相死锁。
        List<WorkPlan> live = workPlanRepository.findOverlapping(
                plan.getWindowStart(), plan.getWindowEnd(),
                List.of(PlanStatus.ACTIVE));
        List<MutexService.Conflict> conflicts = mutexService.findConflicts(plan, live);
        for (MutexService.Conflict c : conflicts) {
            Map<String, Object> refs = new HashMap<>();
            refs.put("otherPlanNo", c.otherPlan().getPlanNo());
            refs.put("otherWorkType", c.otherPlan().getWorkType());
            refs.put("sharedSections", c.sharedSections());
            violations.add(ConditionViolation.of(ConditionCode.MUTEX_CONFLICT,
                    "与计划 " + c.otherPlan().getPlanNo() + "（作业[" + c.otherPlan().getWorkType()
                            + "]）在区段" + c.sharedSections() + "冲突：" + c.matrixReason(),
                    refs));
        }

        return violations;
    }

    private void evaluateQualifications(WorkPlan plan, Instant now, List<ConditionViolation> violations) {
        List<Long> personIds = plan.getAssignees().stream().map(PlanAssignee::getPersonId).toList();
        if (personIds.isEmpty()) {
            return;
        }
        Map<Long, Person> people = new HashMap<>();
        personRepository.findAllById(personIds).forEach(p -> people.put(p.getId(), p));
        List<Qualification> all = qualificationRepository.findByPersonIdIn(personIds);

        for (Long personId : personIds) {
            List<Qualification> mine = all.stream()
                    .filter(q -> q.getPerson().getId().equals(personId))
                    .toList();
            List<Qualification> forWork = mine.stream()
                    .filter(q -> q.getWorkType().equalsIgnoreCase(plan.getWorkType()))
                    .toList();
            String who = people.containsKey(personId)
                    ? people.get(personId).getName() + "(" + people.get(personId).getRole() + ")"
                    : ("人员#" + personId);

            if (forWork.isEmpty()) {
                violations.add(ConditionViolation.of(ConditionCode.QUALIFICATION_MISSING,
                        who + " 缺少作业类型[" + plan.getWorkType() + "]的有效资质",
                        Map.of("personId", personId, "workType", plan.getWorkType())));
                continue;
            }
            // 覆盖区间 [开工时刻, 预计结束]；资质须在预计结束前（含）不失效
            boolean covers = forWork.stream().anyMatch(q -> q.covers(now, plan.getPlannedFinish()));
            if (!covers) {
                Qualification soonest = forWork.stream()
                        .min((x, y) -> x.getValidUntil().compareTo(y.getValidUntil()))
                        .orElseThrow();
                if (soonest.getValidUntil().isBefore(plan.getPlannedFinish())) {
                    violations.add(ConditionViolation.of(ConditionCode.QUALIFICATION_EXPIRES_BEFORE_FINISH,
                            who + " 的[" + plan.getWorkType() + "]资质于 " + soonest.getValidUntil()
                                    + " 失效，早于预计结束 " + plan.getPlannedFinish()
                                    + "，必须重新安排窗口或更换人员",
                            Map.of("personId", personId,
                                    "validUntil", soonest.getValidUntil(),
                                    "plannedFinish", plan.getPlannedFinish())));
                } else {
                    violations.add(ConditionViolation.of(ConditionCode.QUALIFICATION_MISSING,
                            who + " 的[" + plan.getWorkType() + "]资质在开工时刻 " + now
                                    + " 尚未生效（" + soonest.getValidFrom() + " 起生效）",
                            Map.of("personId", personId, "validFrom", soonest.getValidFrom())));
                }
            }
        }
    }
}
