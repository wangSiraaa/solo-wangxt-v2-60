package local.train.blockwindow.repo;

import local.train.blockwindow.domain.DispatchReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DispatchReceiptRepository extends JpaRepository<DispatchReceipt, Long> {

    List<DispatchReceipt> findByBlockWindowIdOrderByIssuedAtAsc(Long blockWindowId);
}
