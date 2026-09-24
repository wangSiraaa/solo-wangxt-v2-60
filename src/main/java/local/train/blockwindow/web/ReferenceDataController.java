package local.train.blockwindow.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import local.train.blockwindow.domain.MutexRule;
import local.train.blockwindow.domain.Personnel;
import local.train.blockwindow.domain.RailSection;
import local.train.blockwindow.repo.MutexRuleRepository;
import local.train.blockwindow.repo.PersonnelRepository;
import local.train.blockwindow.repo.RailSectionRepository;
import local.train.blockwindow.service.ApiConflictException;
import local.train.blockwindow.web.Requests.PersonnelUpsertRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 演练基础数据：区段字典、人员资质、互斥作业矩阵。 */
@RestController
@RequestMapping("/api/ref-data")
@Tag(name = "演练基础数据", description = "区段、人员资质、互斥作业矩阵（仅模拟数据）")
public class ReferenceDataController {

    private final RailSectionRepository sections;
    private final PersonnelRepository personnel;
    private final MutexRuleRepository mutexRules;

    public ReferenceDataController(RailSectionRepository sections,
                                   PersonnelRepository personnel,
                                   MutexRuleRepository mutexRules) {
        this.sections = sections;
        this.personnel = personnel;
        this.mutexRules = mutexRules;
    }

    @GetMapping("/sections")
    @Operation(summary = "模拟区段及相邻关系")
    public List<RailSection> sections() {
        return sections.findAll();
    }

    @GetMapping("/personnel")
    @Operation(summary = "模拟人员及其资质有效期")
    public List<Personnel> personnel() {
        return personnel.findAll();
    }

    @PostMapping("/personnel")
    @Operation(summary = "新增/演练用人员（证件号已存在则冲突）")
    @ResponseStatus(HttpStatus.CREATED)
    public Personnel upsert(@Valid @RequestBody PersonnelUpsertRequest req) {
        if (personnel.findByBadge(req.badge()).isPresent()) {
            throw new ApiConflictException("证件号已存在: " + req.badge());
        }
        if (!req.qualValidUntil().isAfter(req.qualValidFrom())) {
            throw new ApiConflictException("资质失效时间必须晚于生效时间");
        }
        return personnel.save(new Personnel(req.badge(), req.name(), req.role(),
                req.qualification(), req.qualifiedWork(), req.qualValidFrom(), req.qualValidUntil()));
    }

    @GetMapping("/mutex-matrix")
    @Operation(summary = "互斥作业矩阵（矩阵空白即兼容；禁止只比设备编号）")
    public Map<String, Object> matrix() {
        List<Map<String, String>> rules = mutexRules.findAll().stream()
                .map(r -> Map.of("workTypeA", r.getWorkTypeA(),
                        "workTypeB", r.getWorkTypeB(),
                        "note", r.getNote() == null ? "" : r.getNote()))
                .toList();
        return Map.of(
                "rules", rules,
                "evaluation", "仅当 时间重叠 AND 占用区段相交 AND 命中矩阵条目 才判冲突",
                "simulationOnly", true
        );
    }

    @PostMapping("/mutex-matrix")
    @Operation(summary = "追加互斥矩阵条目（演练用）")
    @ResponseStatus(HttpStatus.CREATED)
    public MutexRule addRule(@RequestBody Map<String, String> body) {
        String a = body.get("workTypeA");
        String b = body.get("workTypeB");
        if (a == null || b == null) {
            throw new ApiConflictException("workTypeA / workTypeB 必填");
        }
        boolean dup = mutexRules.findAll().stream().anyMatch(r -> r.matches(a, b));
        if (dup) {
            throw new ApiConflictException("矩阵条目已存在: %s<->%s".formatted(a, b));
        }
        return mutexRules.save(new MutexRule(a, b, body.get("note")));
    }
}
