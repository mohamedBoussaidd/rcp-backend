package com.remipreparateur.performance.objectifperiode.repository;

import com.remipreparateur.performance.objectifperiode.entity.ObjectifPeriode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ObjectifPeriodeRepository extends JpaRepository<ObjectifPeriode, UUID> {

    /** Une seule instance par période (contrainte d'unicité en base). */
    Optional<ObjectifPeriode> findByPeriodeId(UUID periodeId);

    List<ObjectifPeriode> findByClubId(UUID clubId);

    /** Quelles périodes d'un lot ont déjà des objectifs — alimente l'état « défini / à définir ». */
    List<ObjectifPeriode> findByPeriodeIdIn(Collection<UUID> periodeIds);

    long countByModeleId(UUID modeleId);
}
