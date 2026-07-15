package com.remipreparateur.medical.blessure.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** DTOs du suivi de blessure : journal d'évolution + protocole RTP. */
public final class BlessureSuiviDtos {

    private BlessureSuiviDtos() {}

    public record NoteRequest(LocalDate date, @NotBlank String texte) {}

    public record NoteResponse(
            UUID id,
            UUID blessureId,
            LocalDate date,
            String texte,
            UUID deposePar,
            LocalDateTime createdAt) {}

    /** Ajout d'une étape au protocole en cours (insérée en fin). */
    public record EtapeCreateRequest(
            @NotBlank String libelle,
            Short jDebut,
            Short jFin,
            String description) {}

    /** Édition partielle d'une étape : chaque champ null = inchangé. */
    public record EtapeUpdateRequest(
            String statut,
            String libelle,
            Short jDebut,
            Short jFin,
            String description) {}

    /** Réordonnancement : permutation complète des ids des étapes du protocole. */
    public record OrdreRequest(@NotEmpty List<UUID> etapeIds) {}

    public record EtapeResponse(
            UUID id,
            UUID blessureId,
            short ordre,
            String libelle,
            String statut,
            LocalDate dateValidation,
            Short jDebut,
            Short jFin,
            String description) {}
}
