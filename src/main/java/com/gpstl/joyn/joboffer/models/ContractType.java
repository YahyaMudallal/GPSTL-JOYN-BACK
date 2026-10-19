package com.gpstl.joyn.joboffer.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
/**
 * Type de contrat (table de référence contract_types : CDI, APPRENTICESHIP...).
 * Les lignes sont créées par la migration Flyway ; l'application ne fait que les lire.
 */


@Entity 
@Table(name = "contract_types")


public class ContractType {
    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Short id;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    
    
    @Column(name = "label", nullable = false)
    private String label;
    
    protected ContractType(){

    }
    public Short getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }


















}
