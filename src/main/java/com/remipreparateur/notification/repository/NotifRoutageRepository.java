package com.remipreparateur.notification.repository;

import com.remipreparateur.notification.entity.NotifRoutage;
import com.remipreparateur.notification.entity.TypeNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotifRoutageRepository extends JpaRepository<NotifRoutage, UUID> {

    List<NotifRoutage> findByEquipeId(UUID equipeId);

    Optional<NotifRoutage> findByEquipeIdAndType(UUID equipeId, TypeNotification type);
}
