package com.remipreparateur.tactical.exercice.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/** DTOs de la bibliotheque d'exercices (niveau club). */
public final class ExerciceDtos {

    private ExerciceDtos() {}

    public record ExerciceRequest(
            @NotBlank String nom,
            String categorie,
            String type,            // PHYSIQUE / TECHNIQUE / MIXTE (defaut TECHNIQUE)
            Short dureeMinutes,
            String objectif,
            Short intensite,        // 1..5
            String description,
            Integer distanceAttendueM,
            Integer distanceHauteIntensiteM,
            Short nbSprints) {}

    public record ExerciceResponse(
            UUID id,
            String nom,
            String categorie,
            String type,
            Short dureeMinutes,
            String objectif,
            Short intensite,
            String description,
            String schemaJson,
            Integer distanceAttendueM,
            Integer distanceHauteIntensiteM,
            Short nbSprints,
            UUID creeParId,
            String creeParNom,
            UUID equipeOrigineId,
            String equipeOrigineNom,
            boolean modifiable) {}

    /** Sauvegarde du schéma tactique (éditeur Konva). */
    public record SchemaRequest(String schemaJson) {}
}
