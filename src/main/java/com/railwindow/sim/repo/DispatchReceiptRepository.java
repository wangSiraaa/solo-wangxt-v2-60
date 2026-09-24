package com.railwindow.sim.repo;

import java.util.List;

import com.railwindow.sim.domain.DispatchReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispatchReceiptRepository extends JpaRepository<DispatchReceipt, Long> {

    List<DispatchReceipt> findByPlanIdOrderById(Long planId);
}
