package com.remipreparateur.notification.repository;

import com.remipreparateur.notification.entity.NotifPreference;
import com.remipreparateur.notification.entity.TypeNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotifPreferenceRepository extends JpaRepository<NotifPreference, UUID> {

    List<NotifPreference> findByUserId(UUID userId);

    Optional<NotifPreference> findByUserIdAndType(UUID userId, TypeNotification type);
}
