package com.remipreparateur.notification.repository;

import com.remipreparateur.notification.entity.NotifDroitEnvoi;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotifDroitEnvoiRepository extends JpaRepository<NotifDroitEnvoi, UUID> {

    Optional<NotifDroitEnvoi> findByJoueurId(UUID joueurId);

    List<NotifDroitEnvoi> findByEquipeId(UUID equipeId);
}
