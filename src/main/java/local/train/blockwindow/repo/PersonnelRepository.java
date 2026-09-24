package local.train.blockwindow.repo;

import local.train.blockwindow.domain.Personnel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PersonnelRepository extends JpaRepository<Personnel, Long> {
    Optional<Personnel> findByBadge(String badge);
}
