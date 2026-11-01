package com.gpstl.joyn.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gpstl.joyn.common.model.Skill;

import java.util.Optional;

public interface SkillRepository extends JpaRepository<Skill, Long> {

    Optional<Skill> findByNameIgnoreCase(String name);

    /**
     * Recherche alignée sur l'index unique LOWER(name) : au plus une ligne possible.
     */
    @Query("select s from Skill s where lower(s.name) = lower(:name)")
    Optional<Skill> findByNameLower(@Param("name") String name);

    /**
     * Insère la compétence si aucune ne porte déjà ce nom (casse ignorée).
     * Si une autre transaction insère le même nom au même moment, PostgreSQL attend
     * sa fin puis ignore l'insertion au lieu de lever une erreur d'unicité,
     * ce qui annulerait toute la transaction appelante.
     * SQL natif : ON CONFLICT n'existe pas en JPQL.
     */
    @Modifying
    @Query(value = "INSERT INTO skills (name) VALUES (:name) ON CONFLICT ((LOWER(name))) DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(@Param("name") String name);

    /**
     * Chercher ou créer une compétence du catalogue, sans risque de doublon concurrent.
     * Le SELECT qui suit l'insertion voit la ligne déjà validée par l'autre transaction
     * (niveau READ COMMITTED : chaque requête lit les données validées à son lancement).
     */
    default Skill findOrCreate(String name) {
        insertIfAbsent(name);
        return findByNameLower(name).orElseThrow();
    }
}
