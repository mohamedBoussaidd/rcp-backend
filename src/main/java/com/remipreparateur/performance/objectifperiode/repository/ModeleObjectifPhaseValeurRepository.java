package com.remipreparateur.performance.objectifperiode.repository;

import com.remipreparateur.performance.objectifperiode.entity.ModeleObjectifPhaseValeur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ModeleObjectifPhaseValeurRepository
        extends JpaRepository<ModeleObjectifPhaseValeur, UUID> {

    List<ModeleObjectifPhaseValeur> findByPhaseId(UUID phaseId);

    /** Toutes les valeurs d'un modèle en une requête. L'appelant garde une collection non vide. */
    List<ModeleObjectifPhaseValeur> findByPhaseIdIn(Collection<UUID> phaseIds);

    void deleteByPhaseIdIn(Collection<UUID> phaseIds);
}
