package com.remipreparateur.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** DTOs des seances techniques (composees d'exercices de la bibliotheque). */
public final class SeanceTechniqueDtos {

    private SeanceTechniqueDtos() {}

    public record SeanceTechniqueRequest(
            @NotNull LocalDate date,
            LocalTime heureDebut,
            String titre,
            String objectif,
            String description,
            List<UUID> exerciceIds) {}   // ordre = ordre de la liste

    public record ExerciceLigne(
            UUID exerciceId,
            String nom,
            String categorie,
            Short dureeMinutes,
            Short intensite,
            String objectif,
            String description,
            String schemaJson,
            int ordre) {}

    public record SeanceTechniqueResponse(
            UUID id,
            UUID equipeId,
            LocalDate date,
            LocalTime heureDebut,
            String titre,
            String objectif,
            String description,
            String statut,
            String creeParNom,
            int dureeTotaleMinutes,
            Double intensiteMoyenne,
            List<ExerciceLigne> exercices) {}
}
