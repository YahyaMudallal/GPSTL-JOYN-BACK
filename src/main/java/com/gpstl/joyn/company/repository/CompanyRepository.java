package com.gpstl.joyn.company.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.gpstl.joyn.company.model.Company;

import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    // Recherche par nom insensible à la casse
    Optional<Company> findByNameIgnoreCase(String name);

    // Optionnel : recherche par SIRET si fourni plus tard
    Optional<Company> findBySiret(String siret);
    boolean existsBySiret(String siret);
}