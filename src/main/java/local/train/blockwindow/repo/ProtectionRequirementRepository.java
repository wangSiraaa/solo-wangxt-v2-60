package local.train.blockwindow.repo;

import local.train.blockwindow.domain.ProtectionRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProtectionRequirementRepository extends JpaRepository<ProtectionRequirement, Long> {

    List<ProtectionRequirement> findByBlockWindowIdOrderBySectionCodeAscRequirementCodeAsc(Long blockWindowId);
}
