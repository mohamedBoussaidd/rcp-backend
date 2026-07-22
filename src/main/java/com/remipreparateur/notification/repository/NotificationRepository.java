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

    /** Liste paginée filtrée par catégorie (ensemble de types dérivé de la catégorie). */
    Page<Notification> findByDestinataireUserIdAndTypeInOrderByCreatedAtDesc(
            UUID userId, List<TypeNotification> types, Pageable pageable);

    long countByDestinataireUserIdAndLuFalse(UUID userId);

    List<Notification> findByThreadIdOrderByCreatedAtAsc(UUID threadId);

    Page<Notification> findByEmetteurUserIdOrderByCreatedAtDesc(UUID emetteurUserId, Pageable pageable);

    @Modifying
    @Query("update Notification n set n.lu = true, n.luAt = :now " +
            "where n.destinataireUserId = :userId and n.lu = false")
    int marquerToutLu(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    /** Supprime toutes les notifications déjà lues d'un destinataire. Renvoie le nombre supprimé. */
    @Modifying
    @Query("delete from Notification n where n.destinataireUserId = :userId and n.lu = true")
    int supprimerLues(@Param("userId") UUID userId);

    /**
     * Une notification de ce type, pour ce destinataire et ce sujet, a-t-elle été créée
     * depuis {@code depuis} ? Sert à éviter les doublons d'alerte/rappel sur la fenêtre.
     */
    boolean existsByDestinataireUserIdAndTypeAndSujetJoueurIdAndCreatedAtAfter(
            UUID destinataireUserId, TypeNotification type, UUID sujetJoueurId, LocalDateTime depuis);

    boolean existsByDestinataireUserIdAndTypeAndCreatedAtAfter(
            UUID destinataireUserId, TypeNotification type, LocalDateTime depuis);

    /** Une notif de ce type a-t-elle déjà été émise pour cette équipe depuis {@code depuis} ? */
    boolean existsByEquipeIdAndTypeAndCreatedAtAfter(
            UUID equipeId, TypeNotification type, LocalDateTime depuis);

    /**
     * Purge de rétention : supprime les notifications lues antérieures à {@code seuilLues} et les
     * non lues antérieures à {@code seuilNonLues}. Renvoie le nombre de lignes supprimées.
     */
    @Modifying
    @Query("delete from Notification n where (n.lu = true and n.createdAt < :seuilLues) " +
            "or (n.lu = false and n.createdAt < :seuilNonLues)")
    int purgerAnciennes(@Param("seuilLues") LocalDateTime seuilLues,
                        @Param("seuilNonLues") LocalDateTime seuilNonLues);

    /** Nombre de notifications dont le compte destinataire n'existe plus (orphelines). */
    @Query("select count(n) from Notification n where n.destinataireUserId not in " +
            "(select u.id from Utilisateur u)")
    long compterOrphelinesSansDestinataire();

    /** Supprime toutes les notifications d'un destinataire dont le compte n'existe plus (orphelines). */
    @Modifying
    @Query("delete from Notification n where n.destinataireUserId not in " +
            "(select u.id from Utilisateur u)")
    int purgerOrphelinesSansDestinataire();
}
