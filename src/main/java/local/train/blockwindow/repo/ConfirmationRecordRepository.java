package local.train.blockwindow.repo;

import local.train.blockwindow.domain.ConfirmationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConfirmationRecordRepository extends JpaRepository<ConfirmationRecord, Long> {

    List<ConfirmationRecord> findByBlockWindowIdOrderByRecordedAtAsc(Long blockWindowId);

    List<ConfirmationRecord> findByBlockWindowIdAndRecordTypeOrderByRecordedAtAsc(Long blockWindowId, String recordType);
}
