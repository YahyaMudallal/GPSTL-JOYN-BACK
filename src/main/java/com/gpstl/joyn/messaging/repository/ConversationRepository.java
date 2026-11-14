package com.gpstl.joyn.messaging.repository;

import com.gpstl.joyn.messaging.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByApplicationId(Long applicationId);

    Optional<Conversation> findByMatchId(Long matchId);

    boolean existsByApplicationId(Long applicationId);

    /**
     * Crée la conversation d'une candidature si elle n'existe pas encore.
     * Si deux requêtes arrivent en même temps, la contrainte UNIQUE en garde une seule et
     * PostgreSQL ignore la deuxième au lieu de lever une erreur (qui annulerait toute la transaction).
     * SQL natif : ON CONFLICT n'existe pas en JPQL (même méthode que SkillRepository.insertIfAbsent).
     */
    @Modifying
    @Query(value = """
            INSERT INTO conversations (application_id, is_unlocked)
            VALUES (:applicationId, TRUE)
            ON CONFLICT (application_id) DO NOTHING""", nativeQuery = true)
    int insertForApplicationIfAbsent(@Param("applicationId") Long applicationId);

    /** Même chose pour un match (prêt pour le jour où les matchs seront codés). */
    @Modifying
    @Query(value = """
            INSERT INTO conversations (match_id, is_unlocked)
            VALUES (:matchId, TRUE)
            ON CONFLICT (match_id) DO NOTHING""", nativeQuery = true)
    int insertForMatchIfAbsent(@Param("matchId") Long matchId);

    /** Renvoie la conversation de la candidature, en la créant si besoin. */
    default Conversation findOrCreateForApplication(Long applicationId) {
        insertForApplicationIfAbsent(applicationId);
        return findByApplicationId(applicationId).orElseThrow();
    }

    /** Renvoie la conversation du match, en la créant si besoin. */
    default Conversation findOrCreateForMatch(Long matchId) {
        insertForMatchIfAbsent(matchId);
        return findByMatchId(matchId).orElseThrow();
    }
}
