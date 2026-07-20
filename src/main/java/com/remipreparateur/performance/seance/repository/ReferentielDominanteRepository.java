package com.remipreparateur.performance.seance.repository;

import com.remipreparateur.performance.seance.entity.ReferentielDominante;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReferentielDominanteRepository extends JpaRepository<ReferentielDominante, UUID> {

    List<ReferentielDominante> findAllByOrderByFamilleAscOrdreAsc();
}
