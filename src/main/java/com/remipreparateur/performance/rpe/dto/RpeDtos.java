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
     */
    public record RpeRequest(
            @NotNull UUID seanceId,
            @NotNull String seanceType,            // PHYSIQUE | TECHNIQUE
            @NotNull @Min(1) @Max(10) Short rpe,
            Short dureeMinutes,
            String commentaire) {}

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
            LocalDateTime createdAt) {}
}
