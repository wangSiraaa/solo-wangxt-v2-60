package com.railwindow.sim.web;

import java.util.List;

import com.railwindow.sim.domain.MutexRule;
import com.railwindow.sim.domain.Person;
import com.railwindow.sim.domain.Qualification;
import com.railwindow.sim.domain.Section;
import com.railwindow.sim.service.ReferenceService;
import com.railwindow.sim.web.dto.MutexRuleRequest;
import com.railwindow.sim.web.dto.MutexRuleView;
import com.railwindow.sim.web.dto.PersonRequest;
import com.railwindow.sim.web.dto.PersonView;
import com.railwindow.sim.web.dto.QualificationRequest;
import com.railwindow.sim.web.dto.QualificationView;
import com.railwindow.sim.web.dto.SectionRequest;
import com.railwindow.sim.web.dto.SectionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 基础数据管理：人员资质、区段、互斥作业矩阵。
 */
@RestController
@RequestMapping("/api/reference")
@Tag(name = "基础数据", description = "人员、资质、区段、互斥作业矩阵维护（模拟）")
public class ReferenceController {

    private final ReferenceService referenceService;

    public ReferenceController(ReferenceService referenceService) {
        this.referenceService = referenceService;
    }

    @PostMapping("/persons")
    @Operation(summary = "登记人员")
    public ResponseEntity<PersonView> createPerson(@Valid @RequestBody PersonRequest req) {
        Person p = referenceService.createPerson(req.name(), req.role());
        return ResponseEntity.status(HttpStatus.CREATED).body(new PersonView(p.getId(), p.getName(), p.getRole()));
    }

    @GetMapping("/persons")
    @Operation(summary = "人员列表")
    public List<PersonView> listPeople() {
        return referenceService.listPeople().stream()
                .map(p -> new PersonView(p.getId(), p.getName(), p.getRole()))
                .toList();
    }

    @PostMapping("/qualifications")
    @Operation(summary = "为人员登记作业资质及有效期")
    public ResponseEntity<QualificationView> addQualification(@Valid @RequestBody QualificationRequest req) {
        Qualification q = referenceService.addQualification(
                req.personId(), req.workType(), req.validFrom(), req.validUntil());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new QualificationView(q.getId(), q.getPerson().getId(), q.getWorkType(),
                        q.getValidFrom(), q.getValidUntil()));
    }

    @GetMapping("/qualifications")
    @Operation(summary = "资质列表")
    public List<QualificationView> listQualifications() {
        return referenceService.listQualifications().stream()
                .map(q -> new QualificationView(q.getId(), q.getPerson().getId(), q.getWorkType(),
                        q.getValidFrom(), q.getValidUntil()))
                .toList();
    }

    @PostMapping("/sections")
    @Operation(summary = "登记区段（可指定相邻区段 adjacentTo，多区段计划会校验连通性）")
    public ResponseEntity<SectionView> createSection(@Valid @RequestBody SectionRequest req) {
        Section s = referenceService.createSection(req.code(), req.name(), req.adjacentTo());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SectionView(s.getCode(), s.getName(), s.getAdjacentTo()));
    }

    @GetMapping("/sections")
    @Operation(summary = "区段列表")
    public List<SectionView> listSections() {
        return referenceService.listSections().stream()
                .map(s -> new SectionView(s.getCode(), s.getName(), s.getAdjacentTo()))
                .toList();
    }

    @PostMapping("/mutex-rules")
    @Operation(summary = "登记/更新互斥作业矩阵条目（冲突判断的唯一权威依据，不允许只比设备编号）")
    public ResponseEntity<MutexRuleView> putMutexRule(@Valid @RequestBody MutexRuleRequest req) {
        MutexRule r = referenceService.putMutexRule(
                req.workTypeA(), req.workTypeB(), req.exclusive(), req.remark());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MutexRuleView(r.getWorkTypeA(), r.getWorkTypeB(), r.isExclusive(), r.getRemark()));
    }

    @GetMapping("/mutex-rules")
    @Operation(summary = "互斥作业矩阵")
    public List<MutexRuleView> listMutexRules() {
        return referenceService.listMutexRules().stream()
                .map(r -> new MutexRuleView(r.getWorkTypeA(), r.getWorkTypeB(), r.isExclusive(), r.getRemark()))
                .toList();
    }
}
