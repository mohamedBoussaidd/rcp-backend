package com.remipreparateur.performance.objectifperiode.repository;

import com.remipreparateur.performance.objectifperiode.entity.ObjectifPeriodeValeur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ObjectifPeriodeValeurRepository
        extends JpaRepository<ObjectifPeriodeValeur, UUID> {

    List<ObjectifPeriodeValeur> findByObjectifPeriodeId(UUID objectifPeriodeId);

    /** Cases retouchées à la main : à signaler avant d'écraser par une régénération. */
    List<ObjectifPeriodeValeur> findByObjectifPeriodeIdAndModifieManuellementTrue(UUID objectifPeriodeId);

    void deleteByObjectifPeriodeId(UUID objectifPeriodeId);
}
