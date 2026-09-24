package com.railwindow.sim.web;

import java.util.List;

import com.railwindow.sim.domain.CloseoutItemKey;
import com.railwindow.sim.domain.WorkPlan;
import com.railwindow.sim.service.PlanService;
import com.railwindow.sim.service.PlanViews;
import com.railwindow.sim.service.NotFoundException;
import com.railwindow.sim.service.PreconditionEvaluator;
import com.railwindow.sim.web.dto.ActionResponse;
import com.railwindow.sim.web.dto.ArrivalMarkRequest;
import com.railwindow.sim.web.dto.CloseoutItemConfirmRequest;
import com.railwindow.sim.web.dto.CloseoutRequest;
import com.railwindow.sim.web.dto.CreatePlanRequest;
import com.railwindow.sim.web.dto.PlanView;
import com.railwindow.sim.web.dto.PreconditionsView;
import com.railwindow.sim.web.dto.ProtectionConfirmRequest;
import com.railwindow.sim.web.dto.ScheduleRequest;
import com.railwindow.sim.web.dto.StartRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 封锁窗口计划：创建/排定/重新安排、保护与到岗确认、开工、销记。
 */
@RestController
@RequestMapping("/api/plans")
@Tag(name = "封锁窗口计划", description = "计划排定、保护确认、开工与销记（本地模拟，不控制真实设备）")
public class PlanController {

    private final PlanService planService;
    private final PlanViews planViews;
    private final PreconditionEvaluator preconditionEvaluator;

    public PlanController(PlanService planService,
                          PlanViews planViews,
                          PreconditionEvaluator preconditionEvaluator) {
        this.planService = planService;
        this.planViews = planViews;
        this.preconditionEvaluator = preconditionEvaluator;
    }

    @PostMapping
    @Operation(summary = "创建封锁窗口计划（可占用多个相邻区段，状态 DRAFT）")
    public ResponseEntity<PlanView> create(@Valid @RequestBody CreatePlanRequest req) {
        WorkPlan plan = planService.createPlan(req.title(), req.workType(),
                req.windowStart(), req.windowEnd(), req.plannedFinish(),
                req.sectionCodes(), req.personIds());
        return ResponseEntity.status(HttpStatus.CREATED).body(planViews.build(plan));
    }

    @GetMapping
    @Operation(summary = "全部计划")
    public List<PlanView> list() {
        return planService.listPlans().stream().map(planViews::build).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "计划详情（区段保护、人员到岗、资质覆盖、销记清单、模拟回执）")
    public PlanView get(@PathVariable Long id) {
        return planViews.build(planService.requirePlan(id));
    }

    @PostMapping("/{id}/schedule")
    @Operation(summary = "排定/改期封锁窗口；按互斥作业矩阵校验冲突，返回模拟调度受理回执")
    public PlanView schedule(@PathVariable Long id, @Valid @RequestBody ScheduleRequest req) {
        return planViews.build(planService.schedulePlan(
                id, req.windowStart(), req.windowEnd(), req.plannedFinish()));
    }

    @PostMapping("/{id}/reschedule")
    @Operation(summary = "重新安排（如资质在预计结束前失效）：原计划作废，返回接替计划")
    public ResponseEntity<PlanView> reschedule(@PathVariable Long id, @Valid @RequestBody ScheduleRequest req) {
        WorkPlan successor = planService.reschedule(
                id, req.windowStart(), req.windowEnd(), req.plannedFinish());
        return ResponseEntity.status(HttpStatus.CREATED).body(planViews.build(successor));
    }

    @PostMapping("/{id}/protections")
    @Operation(summary = "逐项确认某区段封锁保护到位")
    public PlanView confirmProtection(@PathVariable Long id,
                                      @Valid @RequestBody ProtectionConfirmRequest req) {
        return planViews.build(planService.confirmProtection(
                id, req.sectionCode(), req.personId(), req.note()));
    }

    @PostMapping("/{id}/arrivals")
    @Operation(summary = "登记指派人员到岗")
    public PlanView markArrival(@PathVariable Long id, @Valid @RequestBody ArrivalMarkRequest req) {
        return planViews.build(planService.markArrival(id, req.personId()));
    }

    @GetMapping("/{id}/preconditions")
    @Operation(summary = "开工前条件预检：逐项列出缺失条件（窗口/保护/到岗/资质/矩阵冲突）")
    public PreconditionsView preconditions(@PathVariable Long id) {
        WorkPlan plan = planService.requirePlan(id);
        var violations = preconditionEvaluator.evaluate(plan);
        return new PreconditionsView(plan.getPlanNo(), plan.getStatus().name(),
                violations.isEmpty(), violations);
    }

    @PostMapping("/{id}/start")
    @Operation(summary = """
            发送开工消息：条件全部满足才开工并返回模拟开工许可回执；
            重复请求携带同一 idempotencyKey 时回放首次结果（开工确认重试安全）；
            计划已销记后迟到的开工消息一律 422 拒绝，不得重新开工。
            """)
    public ActionResponse start(@PathVariable Long id, @Valid @RequestBody StartRequest req) {
        return planService.start(id, req.confirmedByPersonId(), req.idempotencyKey());
    }

    @PostMapping("/{id}/closeout-items/{itemKey}")
    @Operation(summary = "逐项确认销记清单项（SITE_REVIEW 现场复核 / PERSONNEL_WITHDRAWAL 人员撤离）")
    public PlanView confirmCloseoutItem(
            @PathVariable Long id,
            @Parameter(description = "SITE_REVIEW 或 PERSONNEL_WITHDRAWAL") @PathVariable String itemKey,
            @Valid @RequestBody CloseoutItemConfirmRequest req) {
        CloseoutItemKey key;
        try {
            key = CloseoutItemKey.valueOf(itemKey);
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("未知销记清单项：" + itemKey
                    + "，可选 SITE_REVIEW / PERSONNEL_WITHDRAWAL");
        }
        return planViews.build(planService.confirmCloseoutItem(id, key,
                req.confirmedByPersonId(), req.idempotencyKey()));
    }

    @PostMapping("/{id}/closeout")
    @Operation(summary = "销记：所有清单项逐项确认后模拟解除封锁；重复请求按 idempotencyKey 回放")
    public ActionResponse closeout(@PathVariable Long id, @Valid @RequestBody CloseoutRequest req) {
        return planService.closeout(id, req.confirmedByPersonId(), req.idempotencyKey());
    }
}
