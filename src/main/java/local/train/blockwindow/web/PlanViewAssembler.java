package local.train.blockwindow.web;

import local.train.blockwindow.domain.BlockWindow;
import local.train.blockwindow.domain.Personnel;
import local.train.blockwindow.domain.PlanAssignment;
import local.train.blockwindow.domain.ProtectionRequirement;
import local.train.blockwindow.repo.PersonnelRepository;
import local.train.blockwindow.repo.PlanAssignmentRepository;
import local.train.blockwindow.repo.ProtectionRequirementRepository;
import local.train.blockwindow.service.BlockWindowService;
import local.train.blockwindow.service.DispatchSimulator;
import local.train.blockwindow.web.Responses.AssignmentView;
import local.train.blockwindow.web.Responses.PlanView;
import local.train.blockwindow.web.Responses.ProtectionView;
import local.train.blockwindow.web.Responses.ReceiptView;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 组装计划详情视图。 */
@Component
public class PlanViewAssembler {

    private final PlanAssignmentRepository assignments;
    private final ProtectionRequirementRepository protections;
    private final PersonnelRepository personnel;
    private final DispatchSimulator dispatchSimulator;

    public PlanViewAssembler(PlanAssignmentRepository assignments,
                             ProtectionRequirementRepository protections,
                             PersonnelRepository personnel,
                             DispatchSimulator dispatchSimulator) {
        this.assignments = assignments;
        this.protections = protections;
        this.personnel = personnel;
        this.dispatchSimulator = dispatchSimulator;
    }

    public PlanView toView(BlockWindow w) {
        List<PlanAssignment> ass = assignments.findByBlockWindowId(w.getId());
        Map<Long, Personnel> people = personnel.findAllById(
                        ass.stream().map(PlanAssignment::getPersonnelId).toList())
                .stream().collect(Collectors.toMap(Personnel::getId, Function.identity()));

        List<AssignmentView> av = ass.stream().map(a -> {
            Personnel p = people.get(a.getPersonnelId());
            boolean covers = p != null
                    && p.getQualifiedWork().contains(w.getWorkType())
                    && p.getQualValidUntil().isAfter(w.getPlannedEnd());
            return new AssignmentView(
                    p == null ? "?" : p.getBadge(),
                    p == null ? "?" : p.getName(),
                    a.getRoleOnPlan(),
                    p == null ? "?" : p.getQualification(),
                    p == null ? null : p.getQualValidUntil(),
                    covers);
        }).toList();

        List<ProtectionView> pv = protections
                .findByBlockWindowIdOrderBySectionCodeAscRequirementCodeAsc(w.getId())
                .stream().map(ProtectionView::from).toList();

        List<ReceiptView> rv = dispatchSimulator.receiptsOf(w.getId())
                .stream().map(ReceiptView::from).toList();

        return new PlanView(w.getId(), w.getPlanNo(), w.getTitle(), w.getWorkType(),
                w.getStatus().name(), List.copyOf(w.getSectionCodes()),
                w.getPlannedStart(), w.getPlannedEnd(),
                BlockWindowService.crossesMidnightUtc(w.getPlannedStart(), w.getPlannedEnd()),
                w.getZoneId(), w.getStartedAt(), w.getReleasedAt(), av, pv, rv);
    }
}
