package com.remipreparateur.performance.seance.dto;

import com.remipreparateur.performance.seance.dto.SeanceDtos.ExerciceLigne;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** DTOs de la bibliothèque de séances-modèles (espace Coaching, niveau club). */
public final class SeanceModeleDtos {

    private SeanceModeleDtos() {}

    /** Création / édition du cadre d'un modèle (les exercices se posent via un endpoint dédié). */
    public record SeanceModeleRequest(
            @NotBlank String nom,
            @NotNull UUID typeSeanceId,
            String objectif,
            Short dureeMinutes,
            Integer objectifDistanceM,
            Short objectifIntensite,
            Integer objectifDistanceHauteIntensiteM,
            String description) {}

    /** Ligne de liste (sans le détail des exercices). */
    public record SeanceModeleResponse(
            UUID id,
            String nom,
            UUID typeSeanceId,
            String typeSeanceLibelle,
            String objectif,
            Short dureeMinutes,
            Integer objectifDistanceM,
            Short objectifIntensite,
            Integer objectifDistanceHauteIntensiteM,
            String description,
            int nbExercices,
            UUID creeParId,
            String creeParNom,
            UUID equipeOrigineId,
            String equipeOrigineNom,
            boolean modifiable) {}

    /** Détail d'un modèle : son cadre + la liste ordonnée des exercices (valeurs effectives) + totaux. */
    public record SeanceModeleDetail(
            SeanceModeleResponse modele,
            List<ExerciceLigne> exercices,
            int dureeTotaleMinutes,
            Double intensiteMoyenne,
            Integer distanceTotaleAttendueM,
            Integer distanceHauteIntensiteTotaleM,
            Integer nbSprintsTotal) {}

    /** Instanciation : crée une vraie séance planifiée à cette date (heure optionnelle). */
    public record PlanifierRequest(
            @NotNull LocalDate date,
            LocalTime heureDebut) {}

    /** Résultat de l'instanciation : identifiant + date de la séance créée dans le calendrier. */
    public record PlanifieResponse(
            UUID seanceId,
            LocalDate date) {}
}
