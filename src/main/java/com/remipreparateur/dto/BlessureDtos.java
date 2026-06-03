package com.remipreparateur.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/** DTOs du module medical (blessures). */
public final class BlessureDtos {

    private BlessureDtos() {}

    public record BlessureRequest(
            @NotNull UUID joueurId,
            @NotNull LocalDate dateBlessure,
            LocalDate dateRetourEffectif,
            String typeBlessure,
            String zoneCorporelle,
            String cote,
            String gravite,
            String causeProbable,
            Boolean recidive,
            String commentaire) {}

    public record BlessureResponse(
            UUID id,
            UUID joueurId,
            String joueurNom,
            String joueurPrenom,
            LocalDate dateBlessure,
            LocalDate dateRetourEffectif,
            String typeBlessure,
            String zoneCorporelle,
            String cote,
            String gravite,
            String causeProbable,
            boolean recidive,
            String commentaire,
            boolean enCours) {}
}
