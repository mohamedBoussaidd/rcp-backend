package com.remipreparateur.performance.seance.repository;

import com.remipreparateur.performance.seance.entity.ReferentielSousPrincipe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReferentielSousPrincipeRepository extends JpaRepository<ReferentielSousPrincipe, UUID> {

    List<ReferentielSousPrincipe> findAllByOrderByPhaseAscOrdreAsc();
}
