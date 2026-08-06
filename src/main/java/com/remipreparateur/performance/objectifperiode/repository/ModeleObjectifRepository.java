package com.remipreparateur.performance.objectifperiode.repository;

import com.remipreparateur.performance.objectifperiode.entity.ModeleObjectif;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ModeleObjectifRepository extends JpaRepository<ModeleObjectif, UUID> {

    List<ModeleObjectif> findByClubIdOrderByNomAsc(UUID clubId);

    /** Modèles proposables pour une période donnée : seuls ceux de son type. */
    List<ModeleObjectif> findByClubIdAndTypePeriodeOrderByNomAsc(UUID clubId, String typePeriode);
}
