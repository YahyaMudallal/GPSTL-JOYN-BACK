package com.gpstl.joyn.repository;

import com.gpstl.joyn.entity.ContractType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContractTypeRepository extends JpaRepository<ContractType, Short> {

    Optional<ContractType> findByCodeIgnoreCase(String code);
}
