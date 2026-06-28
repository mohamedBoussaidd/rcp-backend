package com.remipreparateur.tactical.diaporama.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** DTOs du diaporama de séance (support de présentation réutilisable, niveau club/équipe). */
public final class DiaporamaDtos {

    private DiaporamaDtos() {}

    /** Carte de bibliothèque : un diaporama sans le détail de ses slides. */
    public record DiaporamaResume(
            UUID id,
            String titre,
            String visibilite,
            String statut,
            String createurNom,
            int nbSlides,
            String apercu,        // miniature du 1er slide exploitable (schéma/image), pour la grille
            boolean modifiable,   // l'utilisateur courant est le créateur (ou super-admin)
            boolean supprimable,  // créateur, ou détenteur de diaporama:manage
            LocalDateTime updatedAt) {}

    /** Détail complet : le diaporama + ses slides ordonnées. */
    public record DiaporamaDetail(
            UUID id,
            String titre,
            String visibilite,
            String statut,
            String createurNom,
            boolean modifiable,
            boolean supprimable,
            List<SlideResponse> slides) {}

    public record SlideResponse(
            UUID id,
            String type,
            String titre,
            String schemaJson,
            String apercu,
            String imageSrc,
            String videoUrl,
            String texte,
            String styleJson,
            int ordre) {}

    /** Création d'un diaporama (vide). */
    public record DiaporamaCreateRequest(
            @NotBlank String titre) {}

    /** Mise à jour des méta-données du diaporama. */
    public record DiaporamaUpdateRequest(
            @NotBlank String titre,
            @NotNull @Pattern(regexp = "CLUB|EQUIPE") String visibilite,
            @NotNull @Pattern(regexp = "BROUILLON|PUBLIE") String statut) {}

    /** Ajout / mise à jour d'un slide ; les champs de contenu dépendent du type. */
    public record SlideRequest(
            @NotNull @Pattern(regexp = "SCHEMA|IMAGE|VIDEO_LIEN|TEXTE") String type,
            String titre,
            String schemaJson,
            String apercu,
            String imageSrc,
            String videoUrl,
            String texte,
            String styleJson) {}

    /** Réordonnancement : liste des identifiants de slide dans le nouvel ordre. */
    public record ReordonnerRequest(
            @NotNull List<UUID> ordreIds) {}
}
