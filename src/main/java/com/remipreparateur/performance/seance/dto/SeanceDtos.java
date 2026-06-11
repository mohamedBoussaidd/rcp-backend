package com.remipreparateur.performance.seance.dto;

import java.util.List;
import java.util.UUID;

/** DTOs du contenu d'une séance : ses exercices (référence + overrides) et leurs agrégats. */
public final class SeanceDtos {

    private SeanceDtos() {}

    /** Une ligne d'exercice envoyée par le client. Overrides null = valeur par défaut de l'exercice. */
    public record LigneRequest(
            UUID exerciceId,
            Short dureeMinutes,
            Short intensite,
            Integer distanceAttendueM,
            Integer distanceHauteIntensiteM,
            Short nbSprints) {}

    /** Remplacement complet des exercices d'une séance (ordre = ordre de la liste). */
    public record ExercicesRequest(List<LigneRequest> exercices) {}

    /** Une ligne d'exercice telle qu'affichée : valeurs effectives (override sinon défaut) + libellés. */
    public record ExerciceLigne(
            UUID exerciceId,
            String nom,
            String categorie,
            String type,
            int ordre,
            Short dureeMinutes,
            Short intensite,
            String objectif,
            String description,
            String schemaJson,
            Integer distanceAttendueM,
            Integer distanceHauteIntensiteM,
            Short nbSprints) {}

    /**
     * Contenu d'une séance : ses exercices + agrégats. Les totaux servent à pré-remplir
     * l'objectif d'équipe de la séance (durée = Σ ; distances/sprints = Σ des PHYSIQUE/MIXTE).
     */
    public record ContenuSeance(
            UUID seanceId,
            List<ExerciceLigne> exercices,
            int dureeTotaleMinutes,
            Double intensiteMoyenne,
            Integer distanceTotaleAttendueM,
            Integer distanceHauteIntensiteTotaleM,
            Integer nbSprintsTotal) {}
}
