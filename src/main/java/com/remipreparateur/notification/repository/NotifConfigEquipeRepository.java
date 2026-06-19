package com.remipreparateur.notification.repository;

import com.remipreparateur.notification.entity.NotifConfigEquipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotifConfigEquipeRepository extends JpaRepository<NotifConfigEquipe, UUID> {

    Optional<NotifConfigEquipe> findByEquipeId(UUID equipeId);
}
