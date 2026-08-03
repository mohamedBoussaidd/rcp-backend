package com.remipreparateur.tactical.schema.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** DTOs du partage de schémas aux joueurs (V100). */
public final class SchemaPartageDtos {

    private SchemaPartageDtos() {}

    /**
     * Demande de partage. {@code equipe} = toute l'équipe active ; {@code joueurIds} = des
     * joueurs nommés. Les deux peuvent être combinés (l'équipe plus un rappel nominatif), mais
     * il en faut au moins un — sans destinataire, un partage n'existe pas.
     */
    public record PartageRequest(
            @NotNull UUID schemaId,
            boolean equipe,
            List<UUID> joueurIds,
            String titre,
            String message) {}

    /** Un partage tel que le staff le relit (qui a reçu quoi, et quand). */
    public record PartageResponse(
            UUID id,
            UUID schemaId,
            String schemaNom,
            UUID equipeId,
            UUID joueurId,
            String destinataire,
            String titre,
            String message,
            String parNom,
            LocalDateTime createdAt) {}

    /**
     * Un schéma reçu, tel que le joueur le voit. Le {@code schemaJson} est inclus : le lecteur
     * de la PWA est le même composant que côté staff, il lui faut le contenu.
     */
    public record MonSchemaResponse(
            UUID id,
            UUID schemaId,
            String titre,
            String message,
            String schemaJson,
            String apercu,
            boolean pourMoiSeul,
            LocalDateTime partageLe) {}
}
