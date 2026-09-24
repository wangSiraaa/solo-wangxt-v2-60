package local.train.blockwindow.service;

import local.train.blockwindow.domain.BlockWindow;
import local.train.blockwindow.domain.ConfirmationRecord;
import local.train.blockwindow.domain.DispatchReceipt;
import local.train.blockwindow.domain.Personnel;
import local.train.blockwindow.domain.PlanAssignment;
import local.train.blockwindow.domain.ProtectionRequirement;
import local.train.blockwindow.domain.RailSection;
import local.train.blockwindow.domain.WindowStatus;
import local.train.blockwindow.repo.BlockWindowRepository;
import local.train.blockwindow.repo.ConfirmationRecordRepository;
import local.train.blockwindow.repo.PersonnelRepository;
import local.train.blockwindow.repo.PlanAssignmentRepository;
import local.train.blockwindow.repo.ProtectionRequirementRepository;
import local.train.blockwindow.repo.RailSectionRepository;
import local.train.blockwindow.web.MissingCondition;
import local.train.blockwindow.web.Requests.ReleaseCheckRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 封锁窗口核心业务服务。
 *
 * <p>所有规则均为本地演练逻辑：开工前置条件逐项校验、资质按窗口预计结束时间核对、
 * 冲突走 {@link MutexEvaluator} 互斥矩阵；销记前逐项复核并确认人员撤离；
 * 迟到开工消息只记录、拒绝，绝不改变已销记状态。
 */
@Service
public class BlockWindowService {

    private static final Logger log = LoggerFactory.getLogger(BlockWindowService.class);

    /** 开工必须到齐的计划岗位。 */
    static final List<String> REQUIRED_ROLES =
            List.of("WORK_LEADER", "SAFETY_OFFICER", "CONTACT", "PROTECTOR");

    /** 每个占用区段默认必须逐项确认的保护条件。 */
    static final Map<String, String> DEFAULT_PROTECTION = new LinkedHashMap<>();

    static {
        DEFAULT_PROTECTION.put("STOP_SIGNAL", "停车信号牌已设置");
        DEFAULT_PROTECTION.put("RED_FLAG", "红色防护信号已设置");
        DEFAULT_PROTECTION.put("ADJACENT_WATCH", "邻线来车防护已到位");
    }

    private final BlockWindowRepository windows;
    private final RailSectionRepository sections;
    private final PersonnelRepository personnel;
    private final PlanAssignmentRepository assignments;
    private final ProtectionRequirementRepository protections;
    private final ConfirmationRecordRepository confirmations;
    private final MutexEvaluator mutexEvaluator;
    private final DispatchSimulator dispatchSimulator;
    private final Clock clock;

    public BlockWindowService(BlockWindowRepository windows,
                              RailSectionRepository sections,
                              PersonnelRepository personnel,
                              PlanAssignmentRepository assignments,
                              ProtectionRequirementRepository protections,
                              ConfirmationRecordRepository confirmations,
                              MutexEvaluator mutexEvaluator,
                              DispatchSimulator dispatchSimulator,
                              Clock clock) {
        this.windows = windows;
        this.sections = sections;
        this.personnel = personnel;
        this.assignments = assignments;
        this.protections = protections;
        this.confirmations = confirmations;
        this.mutexEvaluator = mutexEvaluator;
        this.dispatchSimulator = dispatchSimulator;
        this.clock = clock;
    }

    // ---------------------------------------------------------------------
    // 计划
    // ---------------------------------------------------------------------

    @Transactional
    public BlockWindow createPlan(String planNo, String title, String workType, List<String> rawSections,
                                  Instant plannedStart, Instant plannedEnd, String zoneId) {
        if (windows.existsByPlanNo(planNo)) {
            throw new ApiConflictException("计划号已存在: " + planNo);
        }
        if (!plannedEnd.isAfter(plannedStart)) {
            throw new ApiConflictException("计划结束时间必须晚于开始时间");
        }
        List<String> sectionCodes = normalizeAndValidateSections(rawSections);

        BlockWindow window = new BlockWindow(planNo, title, workType, sectionCodes,
                plannedStart, plannedEnd, zoneId);
        windows.saveAndFlush(window);

        // 为每个占用区段生成默认保护条件清单（初始均未确认）
        List<ProtectionRequirement> rows = new ArrayList<>();
        for (String code : sectionCodes) {
            for (Map.Entry<String, String> e : DEFAULT_PROTECTION.entrySet()) {
                rows.add(new ProtectionRequirement(window.getId(), code, e.getKey(), e.getValue()));
            }
        }
        protections.saveAll(rows);
        return window;
    }

    /** 校验：区段全部存在、去重保序、多个区段必须彼此相邻连通（不是任意编号凑数）。 */
    private List<String> normalizeAndValidateSections(List<String> raw) {
        List<String> codes = raw.stream().filter(Objects::nonNull).map(String::trim)
                .distinct().toList();
        if (codes.isEmpty()) {
            throw new ApiConflictException("至少占用一个区段");
        }
        Map<String, RailSection> known = new HashMap<>();
        sections.findAllById(codes).forEach(s -> known.put(s.getCode(), s));
        List<String> unknown = codes.stream().filter(c -> !known.containsKey(c)).toList();
        if (!unknown.isEmpty()) {
            throw new NotFoundException("区段不存在(模拟区段表): " + unknown);
        }
        for (int i = 1; i < codes.size(); i++) {
            List<String> adj = known.get(codes.get(i - 1)).getAdjacentTo();
            if (!adj.contains(codes.get(i))) {
                throw new ApiConflictException(
                        "占用区段不相邻，无法作为同一封锁窗口：%s 与 %s 不在相邻关系表中"
                                .formatted(codes.get(i - 1), codes.get(i)));
            }
        }
        return codes;
    }

    @Transactional(readOnly = true)
    public BlockWindow requireWindow(String planNo) {
        return windows.findByPlanNo(planNo)
                .orElseThrow(() -> new NotFoundException("封锁窗口计划不存在: " + planNo));
    }

    // ---------------------------------------------------------------------
    // 派工与资质
    // ---------------------------------------------------------------------

    @Transactional
    public PlanAssignment assign(String planNo, String badge, String roleOnPlan) {
        BlockWindow w = requireWindow(planNo);
        if (w.getStatus() != WindowStatus.DRAFT) {
            throw new ApiConflictException("计划已开工或已销记，不能再派工: " + planNo);
        }
        Personnel p = personnel.findByBadge(badge)
                .orElseThrow(() -> new NotFoundException("人员不存在(模拟人员表): " + badge));

        List<PlanAssignment> existing = assignments.findByBlockWindowId(w.getId());
        if (existing.stream().anyMatch(a -> a.getPersonnelId().equals(p.getId()))) {
            throw new ApiConflictException("人员 %s 已派入计划 %s".formatted(badge, planNo));
        }
        if (existing.stream().anyMatch(a -> a.getRoleOnPlan().equals(roleOnPlan))) {
            throw new ApiConflictException("计划 %s 的岗位 %s 已有人选".formatted(planNo, roleOnPlan));
        }
        if (!REQUIRED_ROLES.contains(roleOnPlan)) {
            throw new ApiConflictException("未知计划岗位: " + roleOnPlan);
        }
        // 资质在派工当时即做一次静态校验；窗口预计结束前失效仍在开工前再次拦截
        if (!p.getQualifiedWork().contains(w.getWorkType())) {
            throw new ApiConflictException("人员 %s(%s) 不具备 %s 作业资质"
                    .formatted(badge, p.getName(), w.getWorkType()));
        }
        if (p.getQualValidUntil().isBefore(w.getPlannedEnd())) {
            throw new ApiConflictException(
                    "人员 %s(%s) 资质将于 %s 失效，早于计划预计结束 %s，请重新安排具备资格的人员"
                            .formatted(badge, p.getName(), p.getQualValidUntil(), w.getPlannedEnd()));
        }
        return assignments.save(new PlanAssignment(w.getId(), p.getId(), roleOnPlan));
    }

    // ---------------------------------------------------------------------
    // 区段保护
    // ---------------------------------------------------------------------

    @Transactional
    public ProtectionRequirement addProtection(String planNo, String sectionCode,
                                               String requirementCode, String label) {
        BlockWindow w = requireWindow(planNo);
        if (w.getStatus() != WindowStatus.DRAFT) {
            throw new ApiConflictException("计划已开工或已销记，不能再追加保护条件: " + planNo);
        }
        if (!w.getSectionCodes().contains(sectionCode)) {
            throw new NotFoundException("区段 %s 不在计划 %s 占用范围内".formatted(sectionCode, planNo));
        }
        List<ProtectionRequirement> all = protections.findByBlockWindowIdOrderBySectionCodeAscRequirementCodeAsc(w.getId());
        if (all.stream().anyMatch(r -> r.getSectionCode().equals(sectionCode)
                && r.getRequirementCode().equals(requirementCode))) {
            throw new ApiConflictException("保护条件已存在: %s/%s".formatted(sectionCode, requirementCode));
        }
        return protections.save(new ProtectionRequirement(w.getId(), sectionCode, requirementCode, label));
    }

    @Transactional
    public ProtectionRequirement confirmProtection(Long requirementId, String confirmedBy) {
        ProtectionRequirement r = protections.findById(requirementId)
                .orElseThrow(() -> new NotFoundException("保护条件不存在: #" + requirementId));
        BlockWindow w = windows.findById(r.getBlockWindowId())
                .orElseThrow(() -> new NotFoundException("保护条件对应的计划不存在: #" + r.getBlockWindowId()));
        if (w.getStatus() != WindowStatus.DRAFT) {
            throw new ApiConflictException("计划已开工或已销记，保护条件状态不可再变更: " + w.getPlanNo());
        }
        r.confirm(confirmedBy, clock.instant());
        return protections.save(r);
    }

    // ---------------------------------------------------------------------
    // 开工（含确认重试的幂等处理）
    // ---------------------------------------------------------------------

    public record StartOutcome(BlockWindow window, boolean started, boolean replayed,
                               DispatchReceipt receipt, List<MissingCondition> missing) {
    }

    /**
     * 开工确认。
     * <ul>
     *   <li>同一 idempotencyKey 重试：首次成功则原样重放回执（replayed=true）；首次失败则重新校验。</li>
     *   <li>前置条件不齐：抛出 {@link PreconditionNotMetException}，逐项列出缺失条件。</li>
     * </ul>
     */
    @Transactional
    public StartOutcome startWork(String planNo, String idempotencyKey, String operatorBadge) {
        BlockWindow w = requireWindow(planNo);
        Instant now = clock.instant();
        String key = normalizeKey(idempotencyKey, w.getId(), "START");

        if (w.getStatus() == WindowStatus.IN_PROGRESS) {
            if (key.equals(w.getStartIdemKey())) {
                log.info("开工确认幂等重放: plan={} key={}", planNo, key);
                DispatchReceipt latest = latestReceipt(w.getId(), "DISPATCHED");
                return new StartOutcome(w, false, true, latest, List.of());
            }
            throw new ApiConflictException("计划 %s 已开工，不能重复开工".formatted(planNo));
        }
        if (w.getStatus() == WindowStatus.RELEASED) {
            // 迟到的开工消息：绝不让已销记计划重新开工，只留痕
            recordConfirmation(w, "LATE_START_MESSAGE", key, null, null,
                    Map.of("operatorBadge", nz(operatorBadge), "releasedAt",
                            String.valueOf(w.getReleasedAt())),
                    false, "计划已销记(RELEASED)，迟到开工确认被拒绝，状态保持 RELEASED");
            throw new LateStartRejectedException(
                    "计划 %s 已销记，迟到开工消息被拒绝，不允许重新开工".formatted(planNo));
        }

        // 对时间重叠窗口加行锁，保证并发开工按提交顺序串行化竞争同一区段
        List<BlockWindow> lockedCandidates = windows.lockActiveOverlapping(
                w.getPlannedStart(), w.getPlannedEnd(),
                activeStatusNames(WindowStatus.DRAFT, WindowStatus.IN_PROGRESS));

        List<MissingCondition> missing = evaluateStartPreconditions(w, now, lockedCandidates);
        if (!missing.isEmpty()) {
            recordConfirmation(w, "START_CONFIRMATION", key, null, null,
                    Map.of("operatorBadge", nz(operatorBadge), "missingCount", missing.size()),
                    false, "前置条件不满足: " + missing.size() + " 项");
            throw new PreconditionNotMetException("START_PRECONDITION_NOT_MET",
                    "开工前置条件不满足，共缺失 " + missing.size() + " 项", missing);
        }

        w.setStatus(WindowStatus.IN_PROGRESS);
        w.setStartedAt(now);
        w.setStartIdemKey(key);
        windows.save(w);
        DispatchReceipt receipt = dispatchSimulator.issueStartReceipt(w);
        recordConfirmation(w, "START_CONFIRMATION", key, null, null,
                Map.of("operatorBadge", nz(operatorBadge), "receiptNo", receipt.getReceiptNo()),
                true, null);
        return new StartOutcome(w, true, false, receipt, List.of());
    }

    /**
     * 开工前置条件逐项评估。每一项缺失都生成独立的 MissingCondition，
     * 调用方因此能看到“具体缺什么”，而不是笼统的审批失败。
     *
     * @param overlappingCandidates 已持有行锁的时间重叠候选窗口（避免并发竞争漏判）
     */
    private List<MissingCondition> evaluateStartPreconditions(BlockWindow w, Instant now,
                                                              List<BlockWindow> overlappingCandidates) {
        List<MissingCondition> missing = new ArrayList<>();
        Map<String, Personnel> people = loadAssignedPersonnel(w.getId());
        Map<String, PlanAssignment> byRole = new HashMap<>();
        assignments.findByBlockWindowId(w.getId())
                .forEach(a -> byRole.put(a.getRoleOnPlan(), a));

        // 1) 四个必需岗位是否到齐
        for (String role : REQUIRED_ROLES) {
            if (!byRole.containsKey(role)) {
                missing.add(MissingCondition.of("ROLE_NOT_ASSIGNED",
                        "必需岗位 %s 未安排具备资格的人员".formatted(roleZh(role)),
                        Map.of("role", role)));
            }
        }

        // 2) 在岗人员资质：必须覆盖本作业类型，且在窗口【预计结束时间】前不得失效
        for (PlanAssignment a : byRole.values()) {
            Personnel p = people.get(String.valueOf(a.getPersonnelId()));
            if (p == null) {
                continue;
            }
            if (!p.getQualifiedWork().contains(w.getWorkType())) {
                missing.add(MissingCondition.of("PERSONNEL_QUALIFICATION_MISMATCH",
                        "人员 %s(%s) 的资质不覆盖 %s，需重新安排".formatted(p.getBadge(), p.getName(), w.getWorkType()),
                        Map.of("badge", p.getBadge(), "role", a.getRoleOnPlan(),
                                "requiredWorkType", w.getWorkType(),
                                "qualifiedWork", p.getQualifiedWork())));
            }
            if (!p.getQualValidUntil().isAfter(w.getPlannedEnd())) {
                missing.add(MissingCondition.of("QUALIFICATION_EXPIRES_BEFORE_PLANNED_END",
                        "人员 %s(%s) 资质将于 %s 失效，不晚于窗口预计结束 %s，必须重新安排人员"
                                .formatted(p.getBadge(), p.getName(),
                                        p.getQualValidUntil(), w.getPlannedEnd()),
                        Map.of("badge", p.getBadge(), "role", a.getRoleOnPlan(),
                                "qualValidUntil", String.valueOf(p.getQualValidUntil()),
                                "plannedEnd", String.valueOf(w.getPlannedEnd()))));
            }
        }

        // 3) 全部区段的全部保护条件逐项确认
        List<ProtectionRequirement> prs =
                protections.findByBlockWindowIdOrderBySectionCodeAscRequirementCodeAsc(w.getId());
        if (prs.isEmpty()) {
            missing.add(MissingCondition.of("NO_PROTECTION_DEFINED",
                    "计划未登记任何区段保护条件", Map.of("planNo", w.getPlanNo())));
        }
        for (ProtectionRequirement pr : prs) {
            if (!pr.isConfirmed()) {
                missing.add(MissingCondition.of("PROTECTION_NOT_CONFIRMED",
                        "区段 %s 的保护条件[%s]%s 尚未确认"
                                .formatted(pr.getSectionCode(), pr.getRequirementCode(), pr.getLabel()),
                        Map.of("sectionCode", pr.getSectionCode(),
                                "requirementCode", pr.getRequirementCode(),
                                "label", pr.getLabel())));
            }
        }

        // 4) 互斥矩阵冲突（时间重叠 AND 区段相交 AND 命中矩阵）
        // 开工闸门只拦截与【已在作业 IN_PROGRESS】计划的竞争——先开工者取得区段；
        // 尚未开工的 DRAFT 计划不占用区段，只在编制预检中提示，不阻塞对端开工。
        List<MutexEvaluator.MutexHit> hits = findInProgressConflicts(w, overlappingCandidates);
        for (MutexEvaluator.MutexHit hit : hits) {
            BlockWindow other = hit.other();
            missing.add(MissingCondition.of("MUTEX_MATRIX_CONFLICT",
                    "与互斥作业矩阵冲突：计划 %s(%s) 在区段 %s 时间重叠，命中规则 %s↔%s（%s）"
                            .formatted(other.getPlanNo(), other.getWorkType(),
                                    hit.sharedSections(), hit.rule().getWorkTypeA(),
                                    hit.rule().getWorkTypeB(), nz(hit.rule().getNote())),
                    Map.of("otherPlanNo", other.getPlanNo(),
                            "otherWorkType", other.getWorkType(),
                            "otherStatus", other.getStatus().name(),
                            "sharedSections", hit.sharedSections(),
                            "ruleA", hit.rule().getWorkTypeA(),
                            "ruleB", hit.rule().getWorkTypeB())));
        }

        // 5) 时间窗：不得早于计划开始（演练时用注入时钟）；已过预计结束需改期
        if (now.isBefore(w.getPlannedStart())) {
            missing.add(MissingCondition.of("BEFORE_PLANNED_START",
                    "当前时间 %s 早于计划开工时间 %s".formatted(now, w.getPlannedStart()),
                    Map.of("now", String.valueOf(now), "plannedStart", String.valueOf(w.getPlannedStart()))));
        }
        if (!now.isBefore(w.getPlannedEnd())) {
            missing.add(MissingCondition.of("WINDOW_ALREADY_ENDED",
                    "当前时间 %s 已达到/超过窗口预计结束 %s，该计划需重新安排"
                            .formatted(now, w.getPlannedEnd()),
                    Map.of("now", String.valueOf(now), "plannedEnd", String.valueOf(w.getPlannedEnd()))));
        }
        return missing;
    }

    /** 对所有时间重叠的在途计划执行矩阵评估（编制预检：含 DRAFT）。 */
    @Transactional(readOnly = true)
    public List<MutexEvaluator.MutexHit> findConflicts(BlockWindow w) {
        return windows.findActiveOverlapping(w.getPlannedStart(), w.getPlannedEnd(),
                        activeStatusNames(WindowStatus.DRAFT, WindowStatus.IN_PROGRESS))
                .stream()
                .filter(o -> !o.getId().equals(w.getId()))
                .map(o -> mutexEvaluator.evaluate(w, o))
                .filter(Objects::nonNull)
                .toList();
    }

    private static List<String> activeStatusNames(WindowStatus... statuses) {
        return java.util.Arrays.stream(statuses).map(Enum::name).toList();
    }

    /** 开工闸门：在已锁定的重叠候选中，只与已在作业（IN_PROGRESS）的计划竞争区段。 */
    private List<MutexEvaluator.MutexHit> findInProgressConflicts(BlockWindow w,
                                                                  List<BlockWindow> candidates) {
        return candidates.stream()
                .filter(o -> o.getStatus() == WindowStatus.IN_PROGRESS)
                .filter(o -> !o.getId().equals(w.getId()))
                .map(o -> mutexEvaluator.evaluate(w, o))
                .filter(Objects::nonNull)
                .toList();
    }

    private Map<String, Personnel> loadAssignedPersonnel(Long windowId) {
        Map<String, Personnel> map = new HashMap<>();
        for (PlanAssignment a : assignments.findByBlockWindowId(windowId)) {
            personnel.findById(a.getPersonnelId())
                    .ifPresent(p -> map.put(String.valueOf(p.getId()), p));
        }
        return map;
    }

    // ---------------------------------------------------------------------
    // 销记前复核 / 撤离确认 / 销记
    // ---------------------------------------------------------------------

    @Transactional
    public ConfirmationRecord submitReleaseCheck(String planNo, ReleaseCheckRequest req) {
        BlockWindow w = requireWindow(planNo);
        if (w.getStatus() != WindowStatus.IN_PROGRESS) {
            throw new ApiConflictException("只有 IN_PROGRESS 计划可提交销记前复核，当前状态: " + w.getStatus());
        }
        String type = req.checkType();
        if (!"REVIEW".equals(type) && !"EVACUATION".equals(type)) {
            throw new ApiConflictException("checkType 必须为 REVIEW 或 EVACUATION");
        }
        if ("REVIEW".equals(type) && !w.getSectionCodes().contains(req.sectionCode())) {
            throw new NotFoundException("复核区段 %s 不在计划占用范围内".formatted(req.sectionCode()));
        }
        if (!Boolean.TRUE.equals(req.confirmed())) {
            throw new ApiConflictException("销记前复核/撤离项必须逐项确认通过才能提交（演练要求）");
        }
        ConfirmationRecord rec = new ConfirmationRecord(w.getId(), "RELEASE_CHECK", null,
                req.refKey(), req.sectionCode(),
                Map.of("checkType", type, "label", req.label(),
                        "confirmedBy", req.confirmedBy(), "confirmed", true),
                true, null, clock.instant());
        return confirmations.save(rec);
    }

    public record ReleaseOutcome(BlockWindow window, boolean released, boolean replayed,
                                 DispatchReceipt receipt, List<MissingCondition> missing) {
    }

    @Transactional
    public ReleaseOutcome release(String planNo, String idempotencyKey, String operatorBadge) {
        BlockWindow w = requireWindow(planNo);
        Instant now = clock.instant();
        String key = normalizeKey(idempotencyKey, w.getId(), "RELEASE");

        if (w.getStatus() == WindowStatus.RELEASED) {
            if (key.equals(w.getReleaseIdemKey())) {
                DispatchReceipt latest = latestReceipt(w.getId(), "CLOSED");
                return new ReleaseOutcome(w, false, true, latest, List.of());
            }
            throw new ApiConflictException("计划 %s 已销记".formatted(planNo));
        }
        if (w.getStatus() != WindowStatus.IN_PROGRESS) {
            throw new ApiConflictException("计划 %s 尚未开工，不能销记".formatted(planNo));
        }

        List<MissingCondition> missing = evaluateReleasePreconditions(w);
        if (!missing.isEmpty()) {
            recordConfirmation(w, "RELEASE", key, null, null,
                    Map.of("operatorBadge", nz(operatorBadge), "missingCount", missing.size()),
                    false, "销记前置条件不满足: " + missing.size() + " 项");
            throw new PreconditionNotMetException("RELEASE_PRECONDITION_NOT_MET",
                    "销记前置条件不满足，共缺失 " + missing.size() + " 项", missing);
        }

        w.setStatus(WindowStatus.RELEASED);
        w.setReleasedAt(now);
        w.setReleaseIdemKey(key);
        windows.save(w);
        DispatchReceipt receipt = dispatchSimulator.issueReleaseReceipt(w);
        recordConfirmation(w, "RELEASE", key, null, null,
                Map.of("operatorBadge", nz(operatorBadge), "receiptNo", receipt.getReceiptNo()),
                true, null);
        return new ReleaseOutcome(w, true, false, receipt, List.of());
    }

    /**
     * 销记前置：开工时已逐项确认的保护条件必须逐项复核撤除/复原；
     * 每名派工人员必须有撤离确认。
     */
    private List<MissingCondition> evaluateReleasePreconditions(BlockWindow w) {
        List<MissingCondition> missing = new ArrayList<>();
        List<ConfirmationRecord> checks =
                confirmations.findByBlockWindowIdAndRecordTypeOrderByRecordedAtAsc(w.getId(), "RELEASE_CHECK");

        // 1) 保护条件逐项复核（refKey = sectionCode + "/" + requirementCode）
        List<ProtectionRequirement> prs =
                protections.findByBlockWindowIdOrderBySectionCodeAscRequirementCodeAsc(w.getId());
        for (ProtectionRequirement pr : prs) {
            String itemKey = pr.getSectionCode() + "/" + pr.getRequirementCode();
            boolean reviewed = checks.stream().anyMatch(c ->
                    "REVIEW".equals(readString(c.getPayload(), "checkType"))
                        && itemKey.equals(c.getItemKey()));
            if (!reviewed) {
                missing.add(MissingCondition.of("REVIEW_ITEM_MISSING",
                        "销记前缺少逐项复核：区段 %s 的保护条件[%s]%s 未复核"
                                .formatted(pr.getSectionCode(), pr.getRequirementCode(), pr.getLabel()),
                        Map.of("sectionCode", pr.getSectionCode(),
                                "requirementCode", pr.getRequirementCode(),
                                "expectedItemKey", itemKey)));
            }
        }

        // 2) 人员撤离逐项确认（EVACUATION 的 refKey = badge）
        for (Personnel p : loadAssignedPersonnel(w.getId()).values()) {
            boolean evacuated = checks.stream().anyMatch(c ->
                    "EVACUATION".equals(readString(c.getPayload(), "checkType"))
                            && p.getBadge().equals(c.getItemKey()));
            if (!evacuated) {
                missing.add(MissingCondition.of("PERSONNEL_NOT_EVACUATED",
                        "人员 %s(%s) 尚未确认撤离作业区段，销记前必须逐人确认"
                                .formatted(p.getBadge(), p.getName()),
                        Map.of("badge", p.getBadge(), "name", p.getName())));
            }
        }
        return missing;
    }

    // ---------------------------------------------------------------------
    // 迟到消息
    // ---------------------------------------------------------------------

    /**
     * 迟到的开工消息入口（例如消息队列重放/网络重传）。
     * 对 RELEASED 计划只登记拒绝记录并返回 false；状态绝不变更。
     * 对 IN_PROGRESS 计划登记为重复消息；对 DRAFT 计划视为普通开工尝试（走完整校验）。
     */
    @Transactional
    public boolean acceptLateStartMessage(String planNo, String messageId, String operatorBadge) {
        BlockWindow w = requireWindow(planNo);
        Instant now = clock.instant();
        if (w.getStatus() == WindowStatus.RELEASED) {
            recordConfirmation(w, "LATE_START_MESSAGE", nz(messageId), null, null,
                    Map.of("operatorBadge", nz(operatorBadge), "releasedAt",
                            String.valueOf(w.getReleasedAt()), "arrivedAt", String.valueOf(now)),
                    false, "计划已销记(RELEASED)，迟到开工消息被拒绝，状态保持 RELEASED");
            log.warn("迟到开工消息被拒绝（已销记）: plan={} messageId={}", planNo, messageId);
            return false;
        }
        if (w.getStatus() == WindowStatus.IN_PROGRESS) {
            recordConfirmation(w, "LATE_START_MESSAGE", nz(messageId), null, null,
                    Map.of("operatorBadge", nz(operatorBadge), "arrivedAt", String.valueOf(now)),
                    false, "计划已在作业中，迟到开工消息忽略");
            return false;
        }
        recordConfirmation(w, "LATE_START_MESSAGE", nz(messageId), null, null,
                Map.of("operatorBadge", nz(operatorBadge), "arrivedAt", String.valueOf(now)),
                false, "计划未开工，迟到消息转人工：需重新发起开工确认");
        return false;
    }

    // ---------------------------------------------------------------------
    // 辅助
    // ---------------------------------------------------------------------

    private void recordConfirmation(BlockWindow w, String type, String idemKey, String itemKey,
                                    String sectionCode, Map<String, Object> payload,
                                    boolean accepted, String rejectReason) {
        confirmations.save(new ConfirmationRecord(w.getId(), type, idemKey, itemKey, sectionCode,
                payload, accepted, rejectReason, clock.instant()));
    }

    private DispatchReceipt latestReceipt(Long windowId, String status) {
        return dispatchSimulator.receiptsOf(windowId).stream()
                .filter(r -> r.getDispatchStatus().equals(status))
                .reduce((a, b) -> b)
                .orElse(null);
    }

    /**
     * 幂等键按计划隔离存储（内部加窗口 id 前缀），因此不同计划可以安全使用同名客户端键，
     * 同计划同键才触发重放。未提供键时生成一次性键（即不参与重放）。
     */
    private static String normalizeKey(String key, Long windowId, String prefix) {
        if (key == null || key.isBlank()) {
            return prefix + ":w" + windowId + ":ANON-" + System.nanoTime();
        }
        return prefix + ":w" + windowId + ":" + key.trim();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    @SuppressWarnings("unchecked")
    private static String readString(Map<String, Object> payload, String key) {
        if (payload == null) {
            return null;
        }
        Object v = payload.get(key);
        return v == null ? null : v.toString();
    }

    private static String roleZh(String role) {
        return switch (role) {
            case "WORK_LEADER" -> "作业负责人";
            case "SAFETY_OFFICER" -> "安全员";
            case "CONTACT" -> "驻站联络员";
            case "PROTECTOR" -> "现场防护员";
            default -> role;
        };
    }

    /** UTC 日期是否不同，用于展示“跨午夜窗口”。时间本身用 Instant，逻辑上无特殊分支。 */
    public static boolean crossesMidnightUtc(Instant start, Instant end) {
        LocalDate d1 = start.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate d2 = end.atZone(ZoneOffset.UTC).toLocalDate();
        return !d1.equals(d2);
    }

    @Transactional(readOnly = true)
    public List<ProtectionRequirement> protectionsFor(Long windowId) {
        return protections.findByBlockWindowIdOrderBySectionCodeAscRequirementCodeAsc(windowId);
    }

    public Optional<ConfirmationRecord> findConfirmation(Long id) {
        return confirmations.findById(id);
    }

    public List<ConfirmationRecord> confirmationsOf(String planNo) {
        BlockWindow w = requireWindow(planNo);
        return confirmations.findByBlockWindowIdOrderByRecordedAtAsc(w.getId());
    }
}
