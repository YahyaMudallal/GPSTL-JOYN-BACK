package com.gpstl.joyn.common.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gpstl.joyn.common.models.Skill;

import java.util.Optional;

public interface SkillRepository extends JpaRepository<Skill, Long> {

    Optional<Skill> findByNameIgnoreCase(String name);
}
