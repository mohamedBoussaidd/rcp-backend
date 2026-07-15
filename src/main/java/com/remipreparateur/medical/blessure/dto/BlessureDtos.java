package com.remipreparateur.medical.blessure.dto;

import jakarta.validation.constraints.NotBlank;
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
            LocalDate dateRetourPrevue,
            String statut,
            String typeBlessure,
            String typePrecision,
            String zoneCorporelle,
            String zonePrecision,
            String cote,
            String gravite,
            String causeProbable,
            Boolean recidive,
            String commentaire,
            String notesMedicales,
            String qualificationAdministrative) {}

    /** Qualification administrative seule (accessible à blessures:qualify, cf. SecurityConfig). */
    public record QualificationRequest(@NotBlank String qualification) {}

    public record BlessureResponse(
            UUID id,
            UUID joueurId,
            String joueurNom,
            String joueurPrenom,
            LocalDate dateBlessure,
            LocalDate dateRetourEffectif,
            LocalDate dateRetourPrevue,
            String statut,
            String typeBlessure,
            String typePrecision,
            String zoneCorporelle,
            String zonePrecision,
            String cote,
            String gravite,
            String causeProbable,
            boolean recidive,
            String commentaire,
            String notesMedicales,
            String qualificationAdministrative,
            boolean enCours,
            boolean retourConfirme) {}
}
