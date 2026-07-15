package com.remipreparateur.contrat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** DTOs du module Contrats & fiches de paye. */
public final class ContratDtos {

    private ContratDtos() {}

    // ── Contrats (gestion, contrats:manage) ──

    public record ContratRequest(
            @NotNull UUID joueurId,
            @NotBlank String typeContrat,
            @NotNull LocalDate dateDebut,
            LocalDate dateFin,
            String notes) {}

    public record ContratResponse(
            UUID id,
            UUID joueurId,
            String joueurNom,
            String joueurPrenom,
            UUID equipeId,
            String typeContrat,
            LocalDate dateDebut,
            LocalDate dateFin,
            boolean actif,
            Integer joursRestants,
            String nomOriginal,
            String notes,
            LocalDateTime createdAt) {}

    /** Vue président : compteurs + échéances proches. */
    public record ContratStats(
            int total,
            int actifs,
            int expirent90j,
            List<ContratResponse> echeances) {}

    // ── Fiches de paye (gestion) ──

    public record BulletinLigne(
            UUID id,
            UUID joueurId,
            String joueurNom,
            String joueurPrenom,
            LocalDate periode,
            String nomOriginal,
            LocalDateTime deposeLe,
            LocalDateTime notifieLe,
            LocalDateTime premierTelechargementLe) {}

    public record DistributionResultat(int distribues, int notifies) {}

    // ── Espace personnel (/api/membre, self-scope) ──

    public record MonContrat(
            UUID id,
            String typeContrat,
            LocalDate dateDebut,
            LocalDate dateFin,
            boolean actif,
            String nomOriginal) {}

    public record MonBulletin(
            UUID id,
            LocalDate periode,
            String nomOriginal,
            LocalDateTime notifieLe,
            LocalDateTime premierTelechargementLe) {}
}
