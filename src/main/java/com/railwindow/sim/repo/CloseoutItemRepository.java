package com.railwindow.sim.repo;

import java.util.List;
import java.util.Optional;

import com.railwindow.sim.domain.CloseoutItem;
import com.railwindow.sim.domain.CloseoutItemKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CloseoutItemRepository extends JpaRepository<CloseoutItem, Long> {

    List<CloseoutItem> findByPlanIdOrderById(Long planId);

    Optional<CloseoutItem> findByPlanIdAndItemKey(Long planId, CloseoutItemKey itemKey);
}
