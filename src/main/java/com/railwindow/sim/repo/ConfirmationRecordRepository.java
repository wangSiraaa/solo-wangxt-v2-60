package com.railwindow.sim.repo;

import java.util.List;

import com.railwindow.sim.domain.ConfirmationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfirmationRecordRepository extends JpaRepository<ConfirmationRecord, Long> {

    List<ConfirmationRecord> findByPlanIdOrderById(Long planId);
}
