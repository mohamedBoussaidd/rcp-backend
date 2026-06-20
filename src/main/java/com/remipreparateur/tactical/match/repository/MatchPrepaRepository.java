package com.remipreparateur.tactical.match.repository;

import com.remipreparateur.tactical.match.entity.MatchPrepa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface MatchPrepaRepository extends JpaRepository<MatchPrepa, UUID> {
    List<MatchPrepa> findByEquipeIdOrderByDateMatchDescCreatedAtDesc(UUID equipeId);

    /** Matchs publiés (visibles côté joueur), du plus récent au plus ancien. */
    List<MatchPrepa> findByEquipeIdAndPublieTrueOrderByDateMatchDescCreatedAtDesc(UUID equipeId);
}
