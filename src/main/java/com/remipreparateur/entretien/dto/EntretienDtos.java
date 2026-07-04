package com.remipreparateur.entretien.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** DTOs du domaine « Suivi individuel & Entretiens » (staff + espace joueur). */
public final class EntretienDtos {

    private EntretienDtos() {}

    // ──────────────────────────── Axes de travail ────────────────────────────

    /** Création / modification d'un axe. {@code statut} null à la création (défaut EN_COURS). */
    public record AxeRequest(
            @NotBlank @Size(max = 140) String libelle,
            @NotBlank @Size(max = 12) String categorie,
            @Size(max = 12) String statut) {}

    /** Axe avec ses agrégats de suivi (pour le bloc « Axes de travail » de la fiche). */
    public record AxeResponse(
            UUID id,
            UUID joueurId,
            String libelle,
            String categorie,
            String statut,
            int nbEntretiens,
            Integer derniereNote,
            String derniereTendance,
            /** Dernière note d'auto-évaluation du joueur sur cet axe (comparaison staff/joueur). */
            Integer derniereAutoEvalNote,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    // ──────────────────────────── Entretiens ────────────────────────────

    /**
     * Ligne d'axe évaluée dans un entretien. Soit {@code axeTravailId} sur un axe existant,
     * soit création à la volée via {@code nouvelAxeLibelle} + {@code nouvelAxeCategorie}.
     */
    public record LigneAxeRequest(
            UUID axeTravailId,
            @Size(max = 140) String nouvelAxeLibelle,
            @Size(max = 12) String nouvelAxeCategorie,
            @Min(1) @Max(5) Integer note,
            @Size(max = 12) String tendance,
            String commentaire) {}

    /** Création d'un entretien avec ses lignes d'axes, en une requête. */
    public record EntretienRequest(
            @NotNull UUID joueurId,
            @NotBlank @Size(max = 12) String type,
            @NotNull LocalDate dateEntretien,
            String notes,
            @Size(max = 500) String videoUrl,
            UUID seanceId,
            UUID schemaTactiqueId,
            /** true = PARTAGE_JOUEUR dès la création ; défaut false (STAFF). */
            boolean partager,
            @Valid List<LigneAxeRequest> axes) {}

    public record LigneAxeResponse(
            UUID id,
            UUID axeTravailId,
            String axeLibelle,
            String categorie,
            Integer note,
            String tendance,
            String commentaire) {}

    public record EntretienResponse(
            UUID id,
            UUID joueurId,
            String type,
            LocalDate dateEntretien,
            UUID menePar,
            String meneParNom,
            String notes,
            String visibilite,
            /** true si visibilite = PARTAGE_JOUEUR. */
            boolean partage,
            UUID seanceId,
            UUID schemaTactiqueId,
            String videoUrl,
            List<LigneAxeResponse> axes,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    /** Réponse du basculement de visibilité : indique si une notification joueur a été émise. */
    public record VisibiliteResponse(
            UUID id,
            String visibilite,
            boolean partage,
            /** false si la fiche n'a pas de compte lié (pas de notif) — le front adapte son message. */
            boolean notificationEnvoyee) {}

    // ──────────────────────────── Synthèse / progression ────────────────────────────

    public record SynthesePoint(LocalDate date, Integer note, String tendance) {}

    public record SyntheseAxe(
            UUID axeId,
            String libelle,
            String categorie,
            String statut,
            int nbEntretiens,
            List<SynthesePoint> serie,
            Integer derniereAutoEvalNote,
            LocalDateTime derniereAutoEvalDate) {}

    public record SyntheseResponse(UUID joueurId, List<SyntheseAxe> axes) {}

    /** Ligne de la vue équipe « Suivi des entretiens » (un joueur de l'effectif). */
    public record EquipeLigne(
            UUID joueurId,
            String nom,
            String prenom,
            String postePrincipal,
            LocalDate dernierEntretien,
            int nb30j,
            int nb90j,
            int nbVideo,
            int nbTerrain,
            int nbDiscussion) {}

    // ──────────────────────────── Espace joueur ────────────────────────────

    /** Axe vu par le joueur : sa dernière auto-éval + la dernière note staff SI issue d'un partage. */
    public record MonAxeResponse(
            UUID id,
            String libelle,
            String categorie,
            String statut,
            Integer derniereNoteStaff,
            String derniereTendanceStaff,
            Integer maDerniereAutoEvalNote,
            LocalDateTime maDerniereAutoEvalDate) {}

    public record MonEntretienResponse(
            UUID id,
            String type,
            LocalDate dateEntretien,
            String notes,
            String videoUrl,
            List<LigneAxeResponse> axes) {}

    public record AutoEvalRequest(
            @NotNull UUID axeTravailId,
            @NotNull @Min(1) @Max(5) Integer note,
            String commentaire) {}

    public record AutoEvalResponse(
            UUID id,
            UUID axeTravailId,
            Integer note,
            String commentaire,
            LocalDateTime createdAt) {}
}
