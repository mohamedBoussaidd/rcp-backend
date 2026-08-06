package com.remipreparateur.performance.referentiel.repository;

import com.remipreparateur.performance.referentiel.entity.NiveauCompetition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NiveauCompetitionRepository extends JpaRepository<NiveauCompetition, String> {

    List<NiveauCompetition> findAllByOrderByOrdreAsc();

    List<NiveauCompetition> findByActifTrueOrderByOrdreAsc();
}
