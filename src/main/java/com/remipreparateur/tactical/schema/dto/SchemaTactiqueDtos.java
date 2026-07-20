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
}
