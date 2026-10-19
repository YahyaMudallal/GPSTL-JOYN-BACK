package com.gpstl.joyn.joboffer.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gpstl.joyn.joboffer.models.ContractType;

import java.util.Optional;

public interface ContractTypeRepository extends JpaRepository<ContractType, Short> {

    Optional<ContractType> findByCodeIgnoreCase(String code);
}
