package com.remipreparateur.tactical.regles.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

public final class RegleTactiqueDtos {

    private RegleTactiqueDtos() {}

    /** Élément de liste (sans le JSON, potentiellement volumineux). */
    public record RegleTactiqueResume(
            UUID id,
            String type,
            String nom,
            String systeme,
            LocalDateTime updatedAt) {}

    /** Détail complet, JSON inclus. */
    public record RegleTactiqueResponse(
            UUID id,
            String type,
            String nom,
            String systeme,
            String reglesJson,
            LocalDateTime updatedAt) {}

    public record RegleTactiqueRequest(
            @NotBlank @Pattern(regexp = "NOUS|ADVERSAIRE") String type,
            @NotBlank @Size(max = 120) String nom,
            @NotBlank @Size(max = 20) String systeme,
            @NotBlank String reglesJson) {}
}
