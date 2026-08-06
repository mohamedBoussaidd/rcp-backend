package com.remipreparateur.performance.objectifperiode.repository;

import com.remipreparateur.performance.objectifperiode.entity.ModeleObjectifPhase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ModeleObjectifPhaseRepository extends JpaRepository<ModeleObjectifPhase, UUID> {

    List<ModeleObjectifPhase> findByModeleIdOrderByOrdreAsc(UUID modeleId);

    void deleteByModeleId(UUID modeleId);
}
