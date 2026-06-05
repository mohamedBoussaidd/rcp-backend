package com.remipreparateur.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    public record EtapeStatutRequest(@NotBlank String statut) {}

    public record EtapeResponse(
            UUID id,
            UUID blessureId,
            short ordre,
            String libelle,
            String statut,
            LocalDate dateValidation) {}
}
