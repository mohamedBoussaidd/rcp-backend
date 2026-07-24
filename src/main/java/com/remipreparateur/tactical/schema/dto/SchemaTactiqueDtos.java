package com.remipreparateur.tactical.schema.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.UUID;

/** DTOs des schémas tactiques (bibliothèque, niveau club + schémas fournis globaux). */
public final class SchemaTactiqueDtos {

    private SchemaTactiqueDtos() {}

    /**
     * @param fourni demande la création d'un schéma FOURNI (global). Ignoré si l'appelant n'est
     *               pas super-admin — un club ne peut pas fabriquer de contenu global.
     */
    public record SchemaTactiqueRequest(
            @NotBlank String nom,
            String categorie,
            @NotBlank String schemaJson,
            String apercu,
            Boolean fourni) {}

    /**
     * @param fourni      schéma global (posé par le super-admin) plutôt qu'appartenant au club
     * @param modifiable  éditable sur place ; un schéma fourni ne l'est que pour le super-admin
     */
    public record SchemaTactiqueResponse(
            UUID id,
            String nom,
            String categorie,
            String schemaJson,
            String apercu,
            String creeParNom,
            LocalDateTime updatedAt,
            boolean modifiable,
            boolean fourni) {}

    /**
     * Résultat de la recherche cross-club (super-admin) : schéma appartenant à un club, avec le
     * nom du club pour situer l'origine. Pas de {@code schemaJson} — la promotion se fait par id
     * côté serveur ; l'aperçu (miniature) suffit à l'affichage.
     */
    public record SchemaRechercheResponse(
            UUID id,
            String nom,
            String categorie,
            String apercu,
            UUID clubId,
            String clubNom,
            String creeParNom,
            LocalDateTime updatedAt) {}
}
