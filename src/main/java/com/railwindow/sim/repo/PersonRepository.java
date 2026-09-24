package com.railwindow.sim.repo;

import com.railwindow.sim.domain.Person;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonRepository extends JpaRepository<Person, Long> {
}
