package com.remipreparateur.medical.wellness.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** DTOs du ressenti quotidien (wellness, indice de Hooper). */
public final class WellnessDtos {

    private WellnessDtos() {}

    /** Saisie du jour ; date optionnelle (défaut = aujourd'hui). Items sur 1..10. */
    public record WellnessRequest(
            LocalDate date,
            @NotNull @Min(1) @Max(10) Short sommeil,
            @NotNull @Min(1) @Max(10) Short fatigue,
            @NotNull @Min(1) @Max(10) Short douleur,
            @NotNull @Min(1) @Max(10) Short stress,
            @NotNull @Min(1) @Max(10) Short humeur,
            String commentaire,
            // Signalement de gêne optionnel (zone null = pas de gêne).
            String geneZone,
            @Min(1) @Max(10) Short geneIntensite,
            String geneMoment) {}

    public record WellnessResponse(
            UUID id,
            UUID joueurId,
            String joueurNom,
            String joueurPrenom,
            LocalDate date,
            Short sommeil,
            Short fatigue,
            Short douleur,
            Short stress,
            Short humeur,
            /** Score de bien-être 0..100 (items négatifs inversés ; plus haut = mieux). */
            int scoreBienEtre,
            String commentaire,
            String geneZone,
            Short geneIntensite,
            String geneMoment,
            boolean geneTraitee,
            /** Type de résolution : ARCHIVEE | CONVERTIE (null tant que non traitée). */
            String geneResolution,
            LocalDateTime geneTraiteeLe,
            LocalDateTime createdAt) {}
}
