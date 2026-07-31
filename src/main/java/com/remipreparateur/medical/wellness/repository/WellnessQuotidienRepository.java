package com.remipreparateur.medical.wellness.repository;

import com.remipreparateur.medical.wellness.entity.WellnessQuotidien;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WellnessQuotidienRepository extends JpaRepository<WellnessQuotidien, UUID> {
    List<WellnessQuotidien> findByJoueurIdOrderByDateDesc(UUID joueurId);
    Optional<WellnessQuotidien> findByJoueurIdAndDate(UUID joueurId, LocalDate date);
    List<WellnessQuotidien> findAllByOrderByDateDesc();
    List<WellnessQuotidien> findByEquipeIdInOrderByDateDesc(Collection<UUID> equipeIds);

    // ── Fenêtre de dates (couche ressenti du calendrier) ──
    // Les lectures existantes renvoient TOUT l'historique : les appeler pour afficher un mois
    // de calendrier serait un sur-téléchargement massif sur une table qui grossit chaque jour.
    List<WellnessQuotidien> findByDateBetween(LocalDate debut, LocalDate fin);
    List<WellnessQuotidien> findByEquipeIdInAndDateBetween(Collection<UUID> equipeIds, LocalDate debut, LocalDate fin);
    List<WellnessQuotidien> findByJoueurIdAndDateBetween(UUID joueurId, LocalDate debut, LocalDate fin);
}
