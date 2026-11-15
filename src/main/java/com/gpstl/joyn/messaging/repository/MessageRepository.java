package com.gpstl.joyn.messaging.repository;

import com.gpstl.joyn.messaging.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * Côté recruteur : marque comme lus les messages envoyés par le candidat.
     * Les messages des collègues recruteurs ne sont pas concernés.
     *
     * @return le nombre de messages marqués comme lus
     */
    @Modifying
    @Query("""
            update Message m
            set m.readAt = :now
            where m.conversation.id = :conversationId
              and m.readAt is null
              and m.senderUserId = :senderUserId""")
    int markAsReadFromSender(@Param("conversationId") Long conversationId,
                             @Param("senderUserId") Long senderUserId,
                             @Param("now") OffsetDateTime now);

    /**
     * Côté candidat : marque comme lus tous les messages qu'il n'a pas envoyés lui-même.
     *
     * @return le nombre de messages marqués comme lus
     */
    @Modifying
    @Query("""
            update Message m
            set m.readAt = :now
            where m.conversation.id = :conversationId
              and m.readAt is null
              and (m.senderUserId is null or m.senderUserId <> :userId)""")
    int markAsReadNotSentBy(@Param("conversationId") Long conversationId,
                            @Param("userId") Long userId,
                            @Param("now") OffsetDateTime now);
}
