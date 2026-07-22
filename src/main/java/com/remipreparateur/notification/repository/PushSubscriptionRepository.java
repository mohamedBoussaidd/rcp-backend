package com.remipreparateur.notification.repository;

import com.remipreparateur.notification.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

    List<PushSubscription> findByUserId(UUID userId);

    long countByUserId(UUID userId);

    List<PushSubscription> findByUserIdIn(List<UUID> userIds);

    Optional<PushSubscription> findByEndpoint(String endpoint);

    void deleteByEndpoint(String endpoint);

    /** Abonnements dont le compte n'existe plus ou est désactivé (fantômes). */
    @Query("select count(p) from PushSubscription p where p.userId not in " +
            "(select u.id from Utilisateur u where u.actif = true)")
    long compterOrphelins();

    /** Supprime les abonnements de comptes inexistants/désactivés. Renvoie le nombre supprimé. */
    @Modifying
    @Query("delete from PushSubscription p where p.userId not in " +
            "(select u.id from Utilisateur u where u.actif = true)")
    int purgerOrphelins();
}
