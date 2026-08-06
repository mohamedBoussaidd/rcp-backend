package com.remipreparateur.tactical.match.repository;

import com.remipreparateur.tactical.match.entity.MatchPrepa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface MatchPrepaRepository extends JpaRepository<MatchPrepa, UUID> {
    List<MatchPrepa> findByEquipeIdOrderByDateMatchDescCreatedAtDesc(UUID equipeId);

    /** Matchs publiés (visibles côté joueur), du plus récent au plus ancien. */
    List<MatchPrepa> findByEquipeIdAndPublieTrueOrderByDateMatchDescCreatedAtDesc(UUID equipeId);

    /** Matchs d'une équipe sur une fenêtre de dates — détection des semaines à deux matchs. */
    List<MatchPrepa> findByEquipeIdAndDateMatchBetween(UUID equipeId, LocalDate debut, LocalDate fin);

    /** Dossier attaché à une séance du calendrier (V104) — au plus un, garanti par index unique. */
    java.util.Optional<MatchPrepa> findBySeanceId(UUID seanceId);
}
