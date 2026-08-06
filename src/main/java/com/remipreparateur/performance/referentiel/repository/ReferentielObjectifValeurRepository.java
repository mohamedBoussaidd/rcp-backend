package com.remipreparateur.performance.referentiel.repository;

import com.remipreparateur.performance.referentiel.entity.ReferentielObjectifValeur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReferentielObjectifValeurRepository
        extends JpaRepository<ReferentielObjectifValeur, UUID> {

    List<ReferentielObjectifValeur> findByReferentielId(UUID referentielId);

    List<ReferentielObjectifValeur> findByReferentielIdAndContexte(UUID referentielId, String contexte);

    List<ReferentielObjectifValeur> findByReferentielIdAndPosteAndContexte(
            UUID referentielId, String poste, String contexte);

    void deleteByReferentielId(UUID referentielId);
}
