package com.railwindow.sim.repo;

import java.util.List;

import com.railwindow.sim.domain.Qualification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QualificationRepository extends JpaRepository<Qualification, Long> {

    List<Qualification> findByPersonIdIn(List<Long> personIds);
}
