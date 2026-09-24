package local.train.blockwindow.repo;

import local.train.blockwindow.domain.PlanAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanAssignmentRepository extends JpaRepository<PlanAssignment, Long> {

    List<PlanAssignment> findByBlockWindowId(Long blockWindowId);
}
