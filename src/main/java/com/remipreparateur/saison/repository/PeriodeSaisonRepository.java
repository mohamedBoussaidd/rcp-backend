package com.remipreparateur.saison.repository;

import com.remipreparateur.saison.entity.PeriodeSaison;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface PeriodeSaisonRepository extends JpaRepository<PeriodeSaison, UUID> {

    List<PeriodeSaison> findBySaisonIdAndEquipeIdOrderByDateDebutAscOrdreAsc(UUID saisonId, UUID equipeId);

    /**
     * Période d'une équipe couvrant une date donnée — même résolution que {@code contexte.py}
     * côté analytics ({@code WHERE :date BETWEEN date_debut AND date_fin}). Renvoie une liste
     * plutôt qu'un Optional : deux saisons qui se chevauchent ne doivent pas faire éclater
     * l'appelant, qui prend simplement la première.
     */
    List<PeriodeSaison> findByEquipeIdAndDateDebutLessThanEqualAndDateFinGreaterThanEqualOrderByDateDebutAsc(
            UUID equipeId, LocalDate debut, LocalDate fin);

    void deleteBySaisonIdAndEquipeId(UUID saisonId, UUID equipeId);

    void deleteBySaisonId(UUID saisonId);
}
