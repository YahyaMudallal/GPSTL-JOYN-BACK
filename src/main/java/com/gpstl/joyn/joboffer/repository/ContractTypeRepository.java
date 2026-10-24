package com.gpstl.joyn.joboffer.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gpstl.joyn.joboffer.model.ContractType;

import java.util.Optional;

public interface ContractTypeRepository extends JpaRepository<ContractType, Short> {

    Optional<ContractType> findByCodeIgnoreCase(String code);
}
