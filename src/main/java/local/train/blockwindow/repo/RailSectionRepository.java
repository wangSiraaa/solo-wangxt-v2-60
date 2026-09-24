package local.train.blockwindow.repo;

import local.train.blockwindow.domain.RailSection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RailSectionRepository extends JpaRepository<RailSection, String> {
}
