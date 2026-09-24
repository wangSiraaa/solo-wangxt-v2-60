package local.train.blockwindow.repo;

import local.train.blockwindow.domain.MutexRule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MutexRuleRepository extends JpaRepository<MutexRule, Long> {
}
