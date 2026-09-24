package local.train.blockwindow.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import local.train.blockwindow.domain.Personnel;
import local.train.blockwindow.domain.PlanAssignment;
import local.train.blockwindow.domain.ProtectionRequirement;
import local.train.blockwindow.repo.PersonnelRepository;
import local.train.blockwindow.service.BlockWindowService;
import local.train.blockwindow.service.NotFoundException;
import local.train.blockwindow.web.Requests.AssignPersonnelRequest;
import local.train.blockwindow.web.Requests.ConfirmProtectionRequest;
import local.train.blockwindow.web.Requests.CreatePlanRequest;
import local.train.blockwindow.web.Requests.ProtectionRequirementRequest;
import local.train.blockwindow.web.Requests.ReleaseCheckRequest;
import local.train.blockwindow.web.Requests.ReleaseRequest;
import local.train.blockwindow.web.Requests.StartWorkRequest;
import local.train.blockwindow.web.Responses.ProtectionView;
import local.train.blockwindow.web.Responses.ReleaseResultView;
import local.train.blockwindow.web.Responses.StartResultView;
import local.train.blockwindow.domain.BlockWindow;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 封锁窗口演练 API。所有接口只操作本地演练库与模拟回执。
 */
@RestController
@RequestMapping("/api/plans")
@Tag(name = "封锁窗口", description = "计划/派工/区段保护/开工/销记（本地模拟演练）")
public class PlanController {

    private final BlockWindowService service;
    private final PlanViewAssembler assembler;
    private final PersonnelRepository personnelRepository;
    private final Clock clock;

    public PlanController(BlockWindowService service, PlanViewAssembler assembler,
                          PersonnelRepository personnelRepository, Clock clock) {
        this.service = service;
        this.assembler = assembler;
        this.personnelRepository = personnelRepository;
        this.clock = clock;
    }

    @PostMapping
    @Operation(summary = "编制封锁窗口计划（可占用多个相邻区段；仅做矩阵冲突预检，不阻断）")
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody CreatePlanRequest req) {
        BlockWindow w = service.createPlan(req.planNo(), req.title(), req.workType(),
                req.sectionCodes(), req.plannedStart(), req.plannedEnd(), req.zoneId());
        List<Map<String, Object>> conflicts = service.findConflicts(w).stream()
                .map(h -> Map.of("otherPlanNo", h.other().getPlanNo(),
                        "otherWorkType", h.other().getWorkType(),
                        "sharedSections", h.sharedSections(),
                        "mutexRule", h.rule().getWorkTypeA() + "<->" + h.rule().getWorkTypeB(),
                        "note", h.rule().getNote() == null ? "" : h.rule().getNote()))
                .toList();
        // 201 + 矩阵预检提示：重叠不等于冲突，冲突详情按矩阵给出；编制阶段不阻断，开工闸门会拦截
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "plan", assembler.toView(w),
                "mutexPreflight", conflicts,
                "note", "时间重叠且区段相交仅为候选，是否冲突以互斥作业矩阵为准"
        ));
    }

    @GetMapping("/{planNo}")
    @Operation(summary = "查询计划详情（派工、保护项、回执）")
    public Map<String, Object> get(@PathVariable String planNo) {
        return Map.of("plan", assembler.toView(service.requireWindow(planNo)));
    }

    @PostMapping("/{planNo}/assignments")
    @Operation(summary = "安排具备资格的人员到计划岗位（资质须覆盖到窗口预计结束）")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> assign(@PathVariable String planNo,
                                      @Valid @RequestBody AssignPersonnelRequest req) {
        PlanAssignment a = service.assign(planNo, req.badge(), req.roleOnPlan());
        Personnel p = personnelRepository.findById(a.getPersonnelId())
                .orElseThrow(() -> new NotFoundException("人员数据丢失"));
        return Map.of("assignmentId", a.getId(),
                "badge", p.getBadge(), "name", p.getName(),
                "roleOnPlan", a.getRoleOnPlan(),
                "qualValidUntil", p.getQualValidUntil());
    }

    @PostMapping("/{planNo}/protections")
    @Operation(summary = "追加区段保护条件（默认已按区段生成停车信号/红旗/邻线防护三项）")
    @ResponseStatus(HttpStatus.CREATED)
    public ProtectionView addProtection(@PathVariable String planNo,
                                        @Valid @RequestBody ProtectionRequirementRequest req) {
        ProtectionRequirement r = service.addProtection(planNo, req.sectionCode(),
                req.requirementCode(), req.label());
        return ProtectionView.from(r);
    }

    @PostMapping("/protections/{id}/confirm")
    @Operation(summary = "逐项确认区段保护条件")
    public ProtectionView confirmProtection(@PathVariable Long id,
                                            @Valid @RequestBody ConfirmProtectionRequest req) {
        return ProtectionView.from(service.confirmProtection(id, req.confirmedBy()));
    }

    @PostMapping("/{planNo}/start")
    @Operation(summary = "开工确认（带幂等键支持重试；失败返回 422 + 逐项缺失条件，不返回笼统审批失败）")
    public StartResultView start(@PathVariable String planNo,
                                 @RequestBody(required = false) StartWorkRequest req) {
        String key = req == null ? null : req.idempotencyKey();
        String badge = req == null ? null : req.operatorBadge();
        // 前置条件不满足时由全局异常处理器返回 422 + missingConditions
        BlockWindowService.StartOutcome out = service.startWork(planNo, key, badge);
        return new StartResultView(out.started(), out.replayed(), planNo,
                out.window().getStatus().name(), clock.instant(),
                out.receipt() == null ? null : Responses.ReceiptView.from(out.receipt()),
                List.of(),
                out.replayed() ? "开工确认幂等重放：未产生第二次开工" : "开工已受理（模拟调度）");
    }

    @PostMapping("/{planNo}/release-checks")
    @Operation(summary = "销记前逐项复核（保护撤除 REVIEW / 人员撤离 EVACUATION）")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> releaseCheck(@PathVariable String planNo,
                                            @Valid @RequestBody ReleaseCheckRequest req) {
        var rec = service.submitReleaseCheck(planNo, req);
        return Map.of("confirmationId", rec.getId(), "accepted", rec.isAccepted());
    }

    @PostMapping("/{planNo}/release")
    @Operation(summary = "销记（全部复核与撤离项齐备才受理；带幂等键；缺项返回 422 逐项列出）")
    public ReleaseResultView release(@PathVariable String planNo,
                                     @RequestBody(required = false) ReleaseRequest req) {
        String key = req == null ? null : req.idempotencyKey();
        String badge = req == null ? null : req.operatorBadge();
        BlockWindowService.ReleaseOutcome out = service.release(planNo, key, badge);
        return new ReleaseResultView(out.released(), out.replayed(), planNo,
                out.window().getStatus().name(), clock.instant(),
                out.receipt() == null ? null : Responses.ReceiptView.from(out.receipt()),
                List.of(),
                out.replayed() ? "销记幂等重放" : "销记已受理（模拟调度）");
    }

    @PostMapping("/{planNo}/late-start")
    @Operation(summary = "演练专用：投递一条迟到的开工消息，验证已销记计划不会重新开工")
    public Map<String, Object> lateStart(@PathVariable String planNo,
                                         @RequestParam(required = false) String messageId,
                                         @RequestParam(required = false) String operatorBadge) {
        boolean accepted = service.acceptLateStartMessage(planNo, messageId, operatorBadge);
        Map<String, Object> body = new HashMap<>();
        body.put("accepted", accepted);
        body.put("planStatus", service.requireWindow(planNo).getStatus().name());
        body.put("message", accepted
                ? "消息受理"
                : "迟到开工消息被拒绝并留痕，计划状态未发生变化");
        return body;
    }

    @GetMapping("/{planNo}/confirmations")
    @Operation(summary = "查看确认/重试/迟到消息/销记复核审计记录")
    public List<Map<String, Object>> confirmations(@PathVariable String planNo) {
        return service.confirmationsOf(planNo).stream().map(c -> Map.<String, Object>of(
                "id", c.getId(),
                "recordType", c.getRecordType(),
                "idemKey", c.getIdemKey() == null ? "" : c.getIdemKey(),
                "itemKey", c.getItemKey() == null ? "" : c.getItemKey(),
                "sectionCode", c.getSectionCode() == null ? "" : c.getSectionCode(),
                "accepted", c.isAccepted(),
                "rejectReason", c.getRejectReason() == null ? "" : c.getRejectReason(),
                "recordedAt", c.getRecordedAt(),
                "payload", c.getPayload() == null ? Map.of() : c.getPayload()
        )).toList();
    }
}
