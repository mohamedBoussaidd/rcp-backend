package com.remipreparateur.performance.rpe.repository;

import com.remipreparateur.performance.rpe.entity.RpeSeance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RpeSeanceRepository extends JpaRepository<RpeSeance, UUID> {
    List<RpeSeance> findByJoueurIdOrderByDateDesc(UUID joueurId);
    Optional<RpeSeance> findByJoueurIdAndSeanceId(UUID joueurId, UUID seanceId);
    List<RpeSeance> findAllByOrderByDateDesc();
    List<RpeSeance> findByEquipeIdInOrderByDateDesc(Collection<UUID> equipeIds);

    // ── Fenêtre de dates (couche ressenti du calendrier) — cf. WellnessQuotidienRepository ──
    List<RpeSeance> findByDateBetween(LocalDate debut, LocalDate fin);
    List<RpeSeance> findByEquipeIdInAndDateBetween(Collection<UUID> equipeIds, LocalDate debut, LocalDate fin);
    List<RpeSeance> findByJoueurIdAndDateBetween(UUID joueurId, LocalDate debut, LocalDate fin);
}
