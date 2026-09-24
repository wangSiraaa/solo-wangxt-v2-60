package com.railwindow.sim.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railwindow.sim.domain.CloseoutItem;
import com.railwindow.sim.domain.CloseoutItemKey;
import com.railwindow.sim.domain.ConfirmationKind;
import com.railwindow.sim.domain.ConfirmationRecord;
import com.railwindow.sim.domain.DispatchReceipt;
import com.railwindow.sim.domain.IdempotencyRecord;
import com.railwindow.sim.domain.PlanAssignee;
import com.railwindow.sim.domain.PlanSection;
import com.railwindow.sim.domain.PlanStatus;
import com.railwindow.sim.domain.ReceiptKind;
import com.railwindow.sim.domain.Section;
import com.railwindow.sim.domain.WorkPlan;
import com.railwindow.sim.repo.CloseoutItemRepository;
import com.railwindow.sim.repo.ConfirmationRecordRepository;
import com.railwindow.sim.repo.IdempotencyRecordRepository;
import com.railwindow.sim.repo.PersonRepository;
import com.railwindow.sim.repo.SectionRepository;
import com.railwindow.sim.repo.WorkPlanRepository;
import com.railwindow.sim.web.dto.ActionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 封锁窗口计划主服务（本地模拟演练）。
 */
@Service
public class PlanService {

    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final WorkPlanRepository planRepository;
    private final SectionRepository sectionRepository;
    private final PersonRepository personRepository;
    private final CloseoutItemRepository closeoutItemRepository;
    private final ConfirmationRecordRepository confirmationRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final PreconditionEvaluator preconditionEvaluator;
    private final SimulatedDispatchService dispatchService;
    private final PlanViews planViews;
    private final AtomicLong planSequence;

    public PlanService(Clock clock,
                       ObjectMapper objectMapper,
                       WorkPlanRepository planRepository,
                       SectionRepository sectionRepository,
                       PersonRepository personRepository,
                       CloseoutItemRepository closeoutItemRepository,
                       ConfirmationRecordRepository confirmationRepository,
                       IdempotencyRecordRepository idempotencyRepository,
                       PreconditionEvaluator preconditionEvaluator,
                       SimulatedDispatchService dispatchService,
                       PlanViews planViews) {
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.planRepository = planRepository;
        this.sectionRepository = sectionRepository;
        this.personRepository = personRepository;
        this.closeoutItemRepository = closeoutItemRepository;
        this.confirmationRepository = confirmationRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.preconditionEvaluator = preconditionEvaluator;
        this.dispatchService = dispatchService;
        this.planViews = planViews;
        long max = planRepository.findAll().stream().mapToLong(WorkPlan::getId).max().orElse(0);
        this.planSequence = new AtomicLong(max);
    }

    // ------------------------------------------------------------------ 创建

    @Transactional
    public WorkPlan createPlan(String title, String workType,
                               Instant windowStart, Instant windowEnd, Instant plannedFinish,
                               List<String> sectionCodes, List<Long> personIds) {
        validateWindow(windowStart, windowEnd, plannedFinish);
        List<String> distinctCodes = checkSections(sectionCodes);
        checkPeople(personIds);

        long seq = planSequence.incrementAndGet();
        WorkPlan plan = new WorkPlan(String.format("PL-%06d", seq), title, workType.trim().toUpperCase(),
                windowStart, windowEnd, plannedFinish, distinctCodes,
                new ArrayList<>(new LinkedHashSet<>(personIds)));
        return planRepository.save(plan);
    }

    // ------------------------------------------------------------------ 排定

    @Transactional
    public WorkPlan schedulePlan(Long planId, Instant start, Instant end, Instant finish) {
        WorkPlan plan = requirePlan(planId);
        if (plan.getStatus() == PlanStatus.CLOSED || plan.getStatus() == PlanStatus.ACTIVE) {
            throw new IllegalPlanStateException(
                    "计划 " + plan.getPlanNo() + " 当前状态 " + plan.getStatus() + "，不能重新排定");
        }
        validateWindow(start, end, finish);
        // 排定阶段允许窗口重叠：两个计划可同时排定，对同一区段的竞争在“开工”时
        // 按互斥矩阵对在施(ACTIVE)计划裁决——先开工者占用，后开工者得到逐项冲突说明。
        plan.setWindow(start, end, finish);
        plan.setStatus(PlanStatus.SCHEDULED);
        planRepository.save(plan);
        dispatchService.issue(plan, ReceiptKind.SCHEDULE, "ACCEPTED",
                SimulatedDispatchService.schedulePayload(plan, "排定封锁窗口"));
        return plan;
    }

    /**
     * 资质在预计结束前失效等情况下重新安排：原计划作废（RESCHEDULED，不再参与冲突/开工），
     * 生成接替计划并直接排定到新窗口；保护与到岗状态需重新逐项确认。
     */
    @Transactional
    public WorkPlan reschedule(Long planId, Instant start, Instant end, Instant finish) {
        WorkPlan old = requirePlan(planId);
        if (old.getStatus() == PlanStatus.CLOSED) {
            throw new IllegalPlanStateException("计划 " + old.getPlanNo() + " 已销记，不能重新安排");
        }
        if (old.getStatus() == PlanStatus.RESCHEDULED) {
            throw new IllegalPlanStateException("计划 " + old.getPlanNo() + " 已作废，请对其接替计划操作");
        }
        validateWindow(start, end, finish);

        long seq = planSequence.incrementAndGet();
        WorkPlan successor = new WorkPlan(String.format("PL-%06d", seq),
                old.getTitle() + "（重新安排）", old.getWorkType(),
                start, end, finish,
                old.getSections().stream().map(PlanSection::getSectionCode).toList(),
                old.getAssignees().stream().map(PlanAssignee::getPersonId).toList());
        successor.setWindow(start, end, finish);
        successor.setStatus(PlanStatus.SCHEDULED);
        successor = planRepository.save(successor);
        successor.setRelatedPlanId(old.getId());

        old.setStatus(PlanStatus.RESCHEDULED);
        old.setRelatedPlanId(successor.getId());
        planRepository.save(old);

        dispatchService.issue(successor, ReceiptKind.RESCHEDULE, "ACCEPTED",
                SimulatedDispatchService.schedulePayload(successor,
                        "由原计划 " + old.getPlanNo() + " 重新安排"));
        return successor;
    }

    // ------------------------------------------------------------ 保护/到岗

    @Transactional
    public WorkPlan confirmProtection(Long planId, String sectionCode, Long personId, String note) {
        WorkPlan plan = requireScheduledOrActive(planId);
        personRepository.findById(personId)
                .orElseThrow(() -> new NotFoundException("人员#" + personId + " 不存在"));
        PlanSection target = plan.getSections().stream()
                .filter(s -> s.getSectionCode().equals(sectionCode))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "区段 " + sectionCode + " 不在计划 " + plan.getPlanNo() + " 占用范围内"));
        if (!target.isProtectionConfirmed()) {
            target.markProtected(personId);
            confirmationRepository.save(new ConfirmationRecord(plan.getId(),
                    ConfirmationKind.PROTECTION, sectionCode, personId, note, clock.instant(), null));
            planRepository.save(plan);
        }
        return plan;
    }

    @Transactional
    public WorkPlan markArrival(Long planId, Long personId) {
        WorkPlan plan = requireScheduledOrActive(planId);
        PlanAssignee target = plan.getAssignees().stream()
                .filter(a -> a.getPersonId().equals(personId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "人员#" + personId + " 未被指派到计划 " + plan.getPlanNo()));
        if (!target.isArrived()) {
            target.markArrived();
            planRepository.save(plan);
        }
        return plan;
    }

    // ------------------------------------------------------------------ 开工

    @Transactional
    public ActionResponse start(Long planId, Long personId, String idempotencyKey) {
        String idemKey = idempotencyKey == null ? null : "START:" + planId + ":" + idempotencyKey;
        if (idemKey != null) {
            Optional<ActionResponse> replay = replay(idemKey);
            if (replay.isPresent()) {
                return replay.get();
            }
        }

        WorkPlan plan = requirePlan(planId);
        List<ConditionViolation> violations = preconditionEvaluator.evaluate(plan);
        if (!violations.isEmpty()) {
            throw new ConditionsNotMetException(violations);
        }

        Instant now = clock.instant();
        DispatchReceipt receipt = dispatchService.issue(plan, ReceiptKind.START, "PERMISSION_GRANTED",
                SimulatedDispatchService.startPayload(plan));
        plan.markStarted(now, receipt.getReceiptNo());
        planRepository.save(plan);
        // 开工即生成销记清单，必须逐项确认现场复核与人员撤离后方可销记
        if (closeoutItemRepository.findByPlanIdOrderById(plan.getId()).isEmpty()) {
            closeoutItemRepository.save(new CloseoutItem(plan.getId(),
                    CloseoutItemKey.SITE_REVIEW, "现场设备状态复核完成、线路具备放行条件"));
            closeoutItemRepository.save(new CloseoutItem(plan.getId(),
                    CloseoutItemKey.PERSONNEL_WITHDRAWAL, "全部作业人员、机具、材料已撤离限界"));
        }
        confirmationRepository.save(new ConfirmationRecord(plan.getId(),
                ConfirmationKind.START, null, personId,
                "开工确认，回执 " + receipt.getReceiptNo(), now, idempotencyKey));

        ActionResponse response = new ActionResponse(false, planViews.build(plan), PlanViews.receipt(receipt));
        remember(idemKey, planId, response);
        return response;
    }

    // -------------------------------------------------------- 销记清单/销记

    @Transactional
    public WorkPlan confirmCloseoutItem(Long planId, CloseoutItemKey itemKey, Long personId, String idempotencyKey) {
        WorkPlan plan = requirePlan(planId);
        if (plan.getStatus() != PlanStatus.ACTIVE) {
            throw new IllegalPlanStateException(
                    "只有作业中(ACTIVE)的计划才能确认销记清单，当前状态 " + plan.getStatus());
        }
        CloseoutItem item = closeoutItemRepository.findByPlanIdAndItemKey(plan.getId(), itemKey)
                .orElseThrow(() -> new NotFoundException("销记清单项 " + itemKey + " 不存在"));
        if (!item.isConfirmed()) {
            item.confirm(personId, clock.instant());
            closeoutItemRepository.save(item);
            confirmationRepository.save(new ConfirmationRecord(plan.getId(),
                    ConfirmationKind.CLOSEOUT_ITEM, itemKey.name(), personId,
                    "逐项确认：" + item.getLabel(), clock.instant(), idempotencyKey));
        }
        return plan;
    }

    @Transactional
    public ActionResponse closeout(Long planId, Long personId, String idempotencyKey) {
        String idemKey = idempotencyKey == null ? null : "CLOSEOUT:" + planId + ":" + idempotencyKey;
        if (idemKey != null) {
            Optional<ActionResponse> replay = replay(idemKey);
            if (replay.isPresent()) {
                return replay.get();
            }
        }

        WorkPlan plan = requirePlan(planId);
        if (plan.getStatus() == PlanStatus.CLOSED) {
            throw new IllegalPlanStateException("计划 " + plan.getPlanNo() + " 已销记");
        }
        if (plan.getStatus() != PlanStatus.ACTIVE) {
            throw new IllegalPlanStateException(
                    "计划 " + plan.getPlanNo() + " 当前状态 " + plan.getStatus() + "，不能销记");
        }

        List<CloseoutItem> items = closeoutItemRepository.findByPlanIdOrderById(plan.getId());
        List<CloseoutItem> pending = items.stream().filter(i -> !i.isConfirmed()).toList();
        if (!pending.isEmpty()) {
            List<ConditionViolation> violations = List.of(ConditionViolation.of(
                    ConditionCode.CLOSEOUT_ITEMS_PENDING,
                    "销记前仍有 " + pending.size() + " 项未逐项确认："
                            + pending.stream().map(i -> i.getItemKey() + "(" + i.getLabel() + ")").toList(),
                    Map.of("pendingItems", pending.stream().map(i -> i.getItemKey().name()).toList())));
            throw new ConditionsNotMetException(violations);
        }

        Instant now = clock.instant();
        DispatchReceipt receipt = dispatchService.issue(plan, ReceiptKind.CLOSEOUT, "CLOSED",
                SimulatedDispatchService.closeoutPayload(plan));
        plan.markClosed(now);
        planRepository.save(plan);
        confirmationRepository.save(new ConfirmationRecord(plan.getId(),
                ConfirmationKind.CLOSEOUT, null, personId,
                "销记确认，回执 " + receipt.getReceiptNo(), now, idempotencyKey));

        ActionResponse response = new ActionResponse(false, planViews.build(plan), PlanViews.receipt(receipt));
        remember(idemKey, planId, response);
        return response;
    }

    // ------------------------------------------------------------------ 查询

    @Transactional(readOnly = true)
    public WorkPlan requirePlan(Long planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new NotFoundException("计划#" + planId + " 不存在"));
    }

    @Transactional(readOnly = true)
    public List<WorkPlan> listPlans() {
        return planRepository.findAll();
    }

    // ------------------------------------------------------------------ 内部

    private WorkPlan requireScheduledOrActive(Long planId) {
        WorkPlan plan = requirePlan(planId);
        if (plan.getStatus() != PlanStatus.SCHEDULED && plan.getStatus() != PlanStatus.ACTIVE) {
            throw new IllegalPlanStateException(
                    "计划 " + plan.getPlanNo() + " 当前状态 " + plan.getStatus() + "，该确认只允许在排定后进行");
        }
        return plan;
    }

    private void validateWindow(Instant start, Instant end, Instant finish) {
        if (start == null || end == null || finish == null || !start.isBefore(end)) {
            throw new ConditionsNotMetException(List.of(ConditionViolation.of(
                    ConditionCode.INVALID_WINDOW, "封锁窗口开始时刻必须早于结束时刻（跨午夜时用绝对时刻表示）")));
        }
        if (finish.isBefore(start) || finish.isAfter(end)) {
            throw new ConditionsNotMetException(List.of(ConditionViolation.of(
                    ConditionCode.INVALID_WINDOW,
                    "预计结束时刻 " + finish + " 必须落在封锁窗口 [" + start + ", " + end + "] 内")));
        }
    }

    /** 校验区段存在、不重复，且多个区段相互相邻（连通）。 */
    private List<String> checkSections(List<String> codes) {
        List<String> distinct = new ArrayList<>(new LinkedHashSet<>(codes));
        Map<String, Section> found = new HashMap<>();
        sectionRepository.findAllById(distinct).forEach(s -> found.put(s.getCode(), s));
        List<String> missing = distinct.stream().filter(c -> !found.containsKey(c)).toList();
        if (!missing.isEmpty()) {
            throw new ConditionsNotMetException(List.of(ConditionViolation.of(
                    ConditionCode.SECTION_NOT_FOUND, "以下区段不存在：" + missing,
                    Map.of("sectionCodes", missing))));
        }
        if (distinct.size() > 1 && !connected(found, distinct)) {
            throw new ConditionsNotMetException(List.of(ConditionViolation.of(
                    ConditionCode.SECTIONS_NOT_ADJACENT,
                    "计划占用的多个区段必须相邻（连通），请检查区段相邻关系：" + distinct,
                    Map.of("sectionCodes", distinct))));
        }
        return distinct;
    }

    /** 沿 section.adjacent_to 做无向 BFS，要求所选区段全部连通。 */
    private boolean connected(Map<String, Section> sections, List<String> codes) {
        Set<String> chosen = new HashSet<>(codes);
        Set<String> visited = new HashSet<>();
        List<String> queue = new ArrayList<>();
        queue.add(codes.get(0));
        visited.add(codes.get(0));
        while (!queue.isEmpty()) {
            String cur = queue.remove(0);
            String adj = sections.get(cur).getAdjacentTo();
            if (adj != null && chosen.contains(adj) && visited.add(adj)) {
                queue.add(adj);
            }
            // 反向相邻：cur 可能是别人的 adjacentTo
            for (Section s : sections.values()) {
                if (cur.equals(s.getAdjacentTo()) && visited.add(s.getCode())) {
                    queue.add(s.getCode());
                }
            }
        }
        return visited.equals(chosen);
    }

    private void checkPeople(List<Long> personIds) {
        Set<Long> distinct = new LinkedHashSet<>(personIds);
        Set<Long> found = new HashSet<>();
        personRepository.findAllById(distinct).forEach(p -> found.add(p.getId()));
        List<Long> missing = distinct.stream().filter(id -> !found.contains(id)).toList();
        if (!missing.isEmpty()) {
            throw new ConditionsNotMetException(List.of(ConditionViolation.of(
                    ConditionCode.PERSON_NOT_FOUND, "以下人员不存在：" + missing,
                    Map.of("personIds", missing))));
        }
    }

    private Optional<ActionResponse> replay(String operationKey) {
        return idempotencyRepository.findById(operationKey).map(rec -> {
            try {
                ActionResponse original = objectMapper.readValue(rec.getResponseJson(), ActionResponse.class);
                return new ActionResponse(true, original.plan(), original.receipt());
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("幂等回放数据损坏：" + operationKey, e);
            }
        });
    }

    private void remember(String operationKey, Long planId, ActionResponse response) {
        if (operationKey == null) {
            return;
        }
        try {
            idempotencyRepository.save(new IdempotencyRecord(
                    operationKey, planId, objectMapper.writeValueAsString(response), clock.instant()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法记录幂等结果", e);
        }
    }
}
