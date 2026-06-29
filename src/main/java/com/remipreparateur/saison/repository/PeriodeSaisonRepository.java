package com.remipreparateur.saison.repository;

import com.remipreparateur.saison.entity.PeriodeSaison;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PeriodeSaisonRepository extends JpaRepository<PeriodeSaison, UUID> {

    List<PeriodeSaison> findBySaisonIdAndEquipeIdOrderByDateDebutAscOrdreAsc(UUID saisonId, UUID equipeId);

    void deleteBySaisonIdAndEquipeId(UUID saisonId, UUID equipeId);

    void deleteBySaisonId(UUID saisonId);
}
