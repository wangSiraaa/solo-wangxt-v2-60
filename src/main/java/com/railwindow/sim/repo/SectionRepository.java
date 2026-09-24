package com.railwindow.sim.repo;

import java.util.Optional;

import com.railwindow.sim.domain.Section;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectionRepository extends JpaRepository<Section, String> {
    Optional<Section> findByCode(String code);
}
