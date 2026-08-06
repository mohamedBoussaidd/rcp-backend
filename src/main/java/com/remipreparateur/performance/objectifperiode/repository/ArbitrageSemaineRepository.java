package com.remipreparateur.performance.objectifperiode.repository;

import com.remipreparateur.performance.objectifperiode.entity.ArbitrageSemaine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArbitrageSemaineRepository extends JpaRepository<ArbitrageSemaine, UUID> {

    Optional<ArbitrageSemaine> findByEquipeIdAndDateLundi(UUID equipeId, LocalDate dateLundi);

    /** Arbitrages d'une équipe sur une fenêtre — utilisé par le bilan de période. */
    List<ArbitrageSemaine> findByEquipeIdAndDateLundiBetween(UUID equipeId, LocalDate debut, LocalDate fin);
}
