package com.remipreparateur.performance.rpe.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** DTOs du RPE de séance (effort perçu Borg CR-10). */
public final class RpeDtos {

    private RpeDtos() {}

    /**
     * Saisie d'un RPE pour une séance. La date et l'équipe sont résolues côté serveur
     * depuis la séance (intégrité) ; la durée sert au calcul de charge.
     *
     * <p>{@code dureeMinutes} est la durée RÉELLEMENT effectuée par ce joueur, pas celle
     * planifiée : un joueur sorti à la 40e minute d'une séance de 90 min doit voir sa charge
     * calculée sur 40. Le formulaire la pré-remplit avec la durée de la séance et laisse
     * le joueur la corriger. La durée planifiée reste lisible sur la séance elle-même,
     * l'écart est donc toujours calculable.
     */
    public record RpeRequest(
            @NotNull UUID seanceId,
            @NotNull String seanceType,            // PHYSIQUE | TECHNIQUE
            @NotNull @Min(1) @Max(10) Short rpe,
            Short dureeMinutes,
            /** Plaisir ressenti 1..10 (V69, ouvert à la saisie en V91). */
            @Min(1) @Max(10) Short plaisir,
            String commentaire,
            /** Gêne optionnelle déclarée sur cette séance (null = aucune). */
            String geneZone,
            @Min(1) @Max(10) Short geneIntensite,
            String geneMoment) {}                  // EFFORT | APRES | REPOS

    public record RpeResponse(
            UUID id,
            UUID joueurId,
            String joueurNom,
            String joueurPrenom,
            UUID seanceId,
            String seanceType,
            LocalDate date,
            Short rpe,
            Short dureeMinutes,
            /** Charge séance = rpe × durée (null si durée inconnue). */
            Integer charge,
            /** Plaisir ressenti 1..10 (null si non renseigné — ex. saisie PWA). */
            Short plaisir,
            String commentaire,
            /** Titre de la séance notée — évite un aller-retour pour l'afficher. */
            String seanceTitre,
            /** Durée planifiée de la séance : comparée à {@code dureeMinutes} elle révèle
             *  une participation partielle (blessure, sortie anticipée, retard). */
            Short dureePrevueMinutes,
            String geneZone,
            Short geneIntensite,
            String geneMoment,
            boolean geneTraitee,
            String geneResolution,
            LocalDateTime geneTraiteeLe,
            LocalDateTime createdAt) {}
}
