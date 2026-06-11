package com.remipreparateur.tactical.schema.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.UUID;

/** DTOs des schémas tactiques (bibliothèque, niveau club). */
public final class SchemaTactiqueDtos {

    private SchemaTactiqueDtos() {}

    public record SchemaTactiqueRequest(
            @NotBlank String nom,
            String categorie,
            @NotBlank String schemaJson,
            String apercu) {}

    public record SchemaTactiqueResponse(
            UUID id,
            String nom,
            String categorie,
            String schemaJson,
            String apercu,
            String creeParNom,
            LocalDateTime updatedAt,
            boolean modifiable) {}
}
