package com.remipreparateur.performance.referentiel.repository;

import com.remipreparateur.performance.referentiel.entity.ReferentielObjectif;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ReferentielObjectifRepository extends JpaRepository<ReferentielObjectif, UUID> {

    /** Tout le catalogue plateforme, brouillons compris — vue du super-admin. */
    List<ReferentielObjectif> findByClubIdIsNullOrderByNiveauAscVersionDesc();

    /** Les référentiels propres à un club (copies personnalisées). */
    List<ReferentielObjectif> findByClubIdOrderByNomAsc(UUID clubId);

    /**
     * Ce qu'un club peut ADOPTER : les référentiels plateforme publiés, plus ses propres copies.
     * Les brouillons plateforme et les versions archivées en sont exclus — un club ne choisit
     * jamais une version qu'on est en train d'écrire, ni une qu'on a retirée.
     */
    @Query("""
           SELECT r FROM ReferentielObjectif r
           WHERE (r.clubId IS NULL AND r.statut = 'PUBLIE')
              OR (r.clubId = :clubId AND r.statut <> 'ARCHIVE')
           ORDER BY r.niveau ASC, r.nom ASC, r.version DESC
           """)
    List<ReferentielObjectif> cataloguePourClub(@Param("clubId") UUID clubId);

    /** Toute la chaîne de versions d'un niveau plateforme (pour proposer une migration). */
    @Query("""
           SELECT r FROM ReferentielObjectif r
           WHERE r.clubId IS NULL AND r.niveau = :niveau AND r.statut = 'PUBLIE'
           ORDER BY r.version DESC
           """)
    List<ReferentielObjectif> versionsPubliees(@Param("niveau") String niveau);
}
