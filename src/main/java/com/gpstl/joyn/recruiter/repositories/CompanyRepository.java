package com.gpstl.joyn.recruiter.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.gpstl.joyn.recruiter.models.Company;

import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    // Recherche par nom insensible à la casse
    Optional<Company> findByNameIgnoreCase(String name);

    // Optionnel : recherche par SIRET si fourni plus tard
    Optional<Company> findBySiret(String siret);
    boolean existsBySiret(String siret);
}