package com.remipreparateur.performance.evenement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** DTOs des événements extrasportifs (V92). */
public final class EvenementDtos {

    private EvenementDtos() {}

    public record EvenementRequest(
            @NotBlank String type,
            @NotBlank String titre,
            @NotNull LocalDate date,
            LocalDate dateFin,
            LocalTime heureDebut,
            LocalTime heureFin,
            String lieu,
            String description,
            /** null = équipe du contexte actif ; sinon équipe explicite. */
            UUID equipeId,
            /** Vide = toute l'équipe. Sinon, personnes nommément concernées. */
            List<UUID> joueurIds,
            Boolean visibleJoueurs) {}

    public record PersonneConcernee(UUID id, String nom, String prenom) {}

    public record EvenementResponse(
            UUID id,
            String type,
            String titre,
            LocalDate date,
            LocalDate dateFin,
            LocalTime heureDebut,
            LocalTime heureFin,
            String lieu,
            String description,
            UUID equipeId,
            boolean visibleJoueurs,
            /** Personnes ciblées, résolues en nom/prénom pour l'affichage direct. */
            List<PersonneConcernee> concernes) {}
}
