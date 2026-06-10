package com.remipreparateur.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** DTOs du plan de jeu (« document d'identité équipe », niveau équipe). */
public final class PlanDeJeuDtos {

    private PlanDeJeuDtos() {}

    /** Le document complet : ses sections ordonnées + un drapeau d'édition pour l'UI. */
    public record PlanDeJeuResponse(
            UUID id,
            UUID equipeId,
            boolean modifiable,
            List<SectionResponse> sections) {}

    public record SectionResponse(
            UUID id,
            String titre,
            String texte,
            String schemaJson,
            String apercu,
            int ordre,
            LocalDateTime updatedAt) {}

    /** Mise à jour d'une section : titre/texte et éventuellement son schéma (copie). */
    public record SectionUpdateRequest(
            @NotBlank String titre,
            String texte,
            String schemaJson,
            String apercu) {}

    /** Création d'une section libre (sur-mesure) à la fin du document. */
    public record SectionCreateRequest(
            @NotBlank String titre,
            String texte) {}

    /** Réordonnancement : liste des identifiants de section dans le nouvel ordre. */
    public record ReordonnerRequest(
            @NotNull List<UUID> ordreIds) {}
}
