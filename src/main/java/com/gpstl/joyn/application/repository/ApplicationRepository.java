package com.gpstl.joyn.application.repository;

import com.gpstl.joyn.application.model.Application;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    boolean existsByCandidate_IdAndJobOffer_Id(Long candidateId, Long jobOfferId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select application from Application application where application.id = :id")
    Optional<Application> findByIdForUpdate(@Param("id") Long id);
}
