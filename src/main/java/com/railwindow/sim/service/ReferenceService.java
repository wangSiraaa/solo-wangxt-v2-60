package com.railwindow.sim.service;

import java.time.Instant;
import java.util.List;

import com.railwindow.sim.domain.MutexRule;
import com.railwindow.sim.domain.Person;
import com.railwindow.sim.domain.Qualification;
import com.railwindow.sim.domain.Section;
import com.railwindow.sim.repo.MutexRuleRepository;
import com.railwindow.sim.repo.PersonRepository;
import com.railwindow.sim.repo.QualificationRepository;
import com.railwindow.sim.repo.SectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 演练基础数据：人员、区段、资质、互斥作业矩阵的维护。
 */
@Service
public class ReferenceService {

    private final PersonRepository personRepository;
    private final SectionRepository sectionRepository;
    private final QualificationRepository qualificationRepository;
    private final MutexRuleRepository mutexRuleRepository;

    public ReferenceService(PersonRepository personRepository,
                            SectionRepository sectionRepository,
                            QualificationRepository qualificationRepository,
                            MutexRuleRepository mutexRuleRepository) {
        this.personRepository = personRepository;
        this.sectionRepository = sectionRepository;
        this.qualificationRepository = qualificationRepository;
        this.mutexRuleRepository = mutexRuleRepository;
    }

    @Transactional
    public Person createPerson(String name, String role) {
        return personRepository.save(new Person(name, role));
    }

    @Transactional(readOnly = true)
    public List<Person> listPeople() {
        return personRepository.findAll();
    }

    @Transactional
    public Section createSection(String code, String name, String adjacentTo) {
        if (sectionRepository.existsById(code)) {
            throw new IllegalPlanStateException("区段 " + code + " 已存在");
        }
        if (adjacentTo != null && !adjacentTo.isBlank() && !sectionRepository.existsById(adjacentTo)) {
            throw new NotFoundException("相邻区段 " + adjacentTo + " 不存在，请先创建");
        }
        return sectionRepository.save(new Section(code, name, adjacentTo));
    }

    @Transactional(readOnly = true)
    public List<Section> listSections() {
        return sectionRepository.findAll();
    }

    @Transactional
    public Qualification addQualification(Long personId, String workType, Instant validFrom, Instant validUntil) {
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new NotFoundException("人员#" + personId + " 不存在"));
        if (!validFrom.isBefore(validUntil)) {
            throw new ConditionsNotMetException(List.of(ConditionViolation.of(
                    ConditionCode.INVALID_WINDOW, "资质生效时刻必须早于失效时刻")));
        }
        return qualificationRepository.save(
                new Qualification(person, workType.trim().toUpperCase(), validFrom, validUntil));
    }

    @Transactional(readOnly = true)
    public List<Qualification> listQualifications() {
        return qualificationRepository.findAll();
    }

    @Transactional
    public MutexRule putMutexRule(String a, String b, boolean exclusive, String remark) {
        return mutexRuleRepository.save(new MutexRule(a.trim().toUpperCase(), b.trim().toUpperCase(),
                exclusive, remark));
    }

    @Transactional(readOnly = true)
    public List<MutexRule> listMutexRules() {
        return mutexRuleRepository.findAll();
    }
}
