package com.remipreparateur.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/** DTOs pour la gestion des clubs (espace super-admin). */
public final class ClubDtos {

    private ClubDtos() {}

    public record PresidentInput(
            @NotBlank @Email String email,
            @NotBlank String nom,
            @NotBlank String prenom,
            @NotBlank String motDePasse) {}

    public record ClubCreateRequest(
            @NotBlank String nom,
            String logo,
            @NotNull @Valid PresidentInput president) {}

    public record ClubUpdateRequest(
            @NotBlank String nom,
            String logo) {}

    public record ClubResponse(
            UUID id,
            String nom,
            String logo,
            LocalDate dateCreation,
            UUID presidentId,
            String presidentEmail,
            String presidentNom,
            String presidentPrenom,
            long nbEquipes) {}
}
