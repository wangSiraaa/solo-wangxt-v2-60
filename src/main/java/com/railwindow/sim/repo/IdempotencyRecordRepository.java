package com.railwindow.sim.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.railwindow.sim.domain.IdempotencyRecord;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {
}
