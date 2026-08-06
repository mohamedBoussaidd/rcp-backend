package com.remipreparateur.performance.referentiel.repository;

import com.remipreparateur.performance.referentiel.entity.ClubReferentiel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClubReferentielRepository extends JpaRepository<ClubReferentiel, UUID> {

    List<ClubReferentiel> findByClubId(UUID clubId);

    /** Surcharge d'une équipe précise. */
    Optional<ClubReferentiel> findByClubIdAndEquipeId(UUID clubId, UUID equipeId);

    /** Adoption par défaut du club (la ligne sans équipe). */
    Optional<ClubReferentiel> findByClubIdAndEquipeIdIsNull(UUID clubId);

    /** Combien de clubs / d'équipes sont épinglés sur ce référentiel (garde-fou de suppression). */
    long countByReferentielId(UUID referentielId);

    /**
     * Usage du catalogue : {@code [referentielId, nombre de clubs distincts]}.
     * Sert à répondre à « qui est resté sur une vieille version ? » avant d'archiver.
     */
    @Query("""
           SELECT c.referentielId, COUNT(DISTINCT c.clubId)
           FROM ClubReferentiel c
           GROUP BY c.referentielId
           """)
    List<Object[]> usageParReferentiel();

    /** Clubs épinglés sur un référentiel donné (identifiants distincts). */
    @Query("SELECT DISTINCT c.clubId FROM ClubReferentiel c WHERE c.referentielId = :referentielId")
    List<UUID> clubsUtilisateurs(@Param("referentielId") UUID referentielId);
}
