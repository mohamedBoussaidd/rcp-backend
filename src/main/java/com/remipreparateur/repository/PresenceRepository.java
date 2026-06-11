package com.remipreparateur.repository;

import com.remipreparateur.entity.Presence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PresenceRepository extends JpaRepository<Presence, UUID> {

    List<Presence> findBySeanceId(UUID seanceId);

    List<Presence> findByJoueurId(UUID joueurId);

    Optional<Presence> findBySeanceIdAndJoueurId(UUID seanceId, UUID joueurId);

    void deleteBySeanceId(UUID seanceId);
}
