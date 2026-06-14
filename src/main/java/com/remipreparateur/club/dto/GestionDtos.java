package com.remipreparateur.club.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** DTOs pour la gestion d'un club par son president (equipes + membres). */
public final class GestionDtos {

    private GestionDtos() {}

    // ── Equipes ──
    public record EquipeRequest(
            @NotBlank String nom,
            String categorie) {}

    public record EquipeResponse(
            UUID id,
            String nom,
            String categorie,
            UUID clubId,
            long nbMembres) {}

    // ── Membres ──
    public record MembreCreateRequest(
            @NotBlank @Email String email,
            @NotBlank String nom,
            @NotBlank String prenom,
            @NotBlank String motDePasse,
            @NotBlank String role,        // ENTRAINEUR | PREPARATEUR | MEDICAL | ADMINISTRATIF | JOUEUR
            String specialite,
            UUID equipeId,
            UUID joueurId) {}

    public record MembreUpdateRequest(
            String role,
            String specialite,
            UUID equipeId,
            Boolean actif) {}

    /** Liaison d'un compte JOUEUR à une fiche joueur existante (posée a posteriori). */
    public record LierFicheRequest(
            @NotNull UUID joueurId) {}

    public record MembreResponse(
            UUID id,
            String email,
            String nom,
            String prenom,
            String role,
            String specialite,
            UUID equipeId,
            UUID joueurId,
            boolean actif) {}

    // ── Vue agregee "mon club" ──
    public record MonClubResponse(
            UUID clubId,
            String clubNom,
            String clubLogo,
            List<EquipeResponse> equipes,
            List<MembreResponse> membres) {}
}
