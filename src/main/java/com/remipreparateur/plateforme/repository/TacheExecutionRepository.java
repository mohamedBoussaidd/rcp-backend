package com.remipreparateur.plateforme.repository;

import com.remipreparateur.plateforme.entity.TacheExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TacheExecutionRepository extends JpaRepository<TacheExecution, UUID> {

    /** Dernière exécution connue d'une tâche (pour l'affichage du statut). */
    Optional<TacheExecution> findTopByCodeOrderByStartedAtDesc(String code);
}
