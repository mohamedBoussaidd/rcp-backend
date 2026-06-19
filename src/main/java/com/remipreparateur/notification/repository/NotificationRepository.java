package com.remipreparateur.notification.repository;

import com.remipreparateur.notification.entity.Notification;
import com.remipreparateur.notification.entity.TypeNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByDestinataireUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByDestinataireUserIdAndLuFalse(UUID userId);

    List<Notification> findByThreadIdOrderByCreatedAtAsc(UUID threadId);

    Page<Notification> findByEmetteurUserIdOrderByCreatedAtDesc(UUID emetteurUserId, Pageable pageable);

    @Modifying
    @Query("update Notification n set n.lu = true, n.luAt = :now " +
            "where n.destinataireUserId = :userId and n.lu = false")
    int marquerToutLu(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    /**
     * Une notification de ce type, pour ce destinataire et ce sujet, a-t-elle été créée
     * depuis {@code depuis} ? Sert à éviter les doublons d'alerte/rappel sur la fenêtre.
     */
    boolean existsByDestinataireUserIdAndTypeAndSujetJoueurIdAndCreatedAtAfter(
            UUID destinataireUserId, TypeNotification type, UUID sujetJoueurId, LocalDateTime depuis);

    boolean existsByDestinataireUserIdAndTypeAndCreatedAtAfter(
            UUID destinataireUserId, TypeNotification type, LocalDateTime depuis);
}
