package com.remipreparateur.performance.evenement.repository;

import com.remipreparateur.performance.evenement.entity.Evenement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface EvenementRepository extends JpaRepository<Evenement, UUID> {

    /**
     * Événements du club qui CHEVAUCHENT la période. Un événement sur plusieurs jours doit
     * apparaître sur tous les jours qu'il couvre, y compris quand la période affichée n'en
     * contient que le milieu — d'où le test de chevauchement plutôt qu'un simple BETWEEN.
     */
    @Query("""
        SELECT e FROM Evenement e
        WHERE e.clubId = :club
          AND e.date <= :fin
          AND COALESCE(e.dateFin, e.date) >= :debut
        ORDER BY e.date, e.heureDebut
        """)
    List<Evenement> findChevauchant(@Param("club") UUID club,
                                    @Param("debut") LocalDate debut,
                                    @Param("fin") LocalDate fin);
}
