package com.remipreparateur.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/** DTOs des formations personnalisées (niveau club). */
public final class FormationDtos {

    private FormationDtos() {}

    public record FormationRequest(
            @NotBlank String nom,
            String couleur,
            @NotBlank String positionsJson) {}

    public record FormationResponse(
            UUID id,
            String nom,
            String couleur,
            String positionsJson,
            String creeParNom,
            boolean modifiable) {}
}
