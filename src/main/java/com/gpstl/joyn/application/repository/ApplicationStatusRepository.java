package com.gpstl.joyn.application.repository;

import com.gpstl.joyn.application.model.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApplicationStatusRepository extends JpaRepository<ApplicationStatus, Short> {

    Optional<ApplicationStatus> findByCode(String code);
}
