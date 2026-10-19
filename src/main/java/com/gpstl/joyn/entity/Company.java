package com.gpstl.joyn.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
/**
 * Entreprise (table companies).
 * Seules les colonnes utiles a la création d'offre sont mappées pour l'instant;
 * les autres colonnes de la tables pourront etre ajoutées par les taches qui en ont besoin.
 * 
 */

@Entity 
@Table(name = "companies")


public class Company {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    protected Company() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}

