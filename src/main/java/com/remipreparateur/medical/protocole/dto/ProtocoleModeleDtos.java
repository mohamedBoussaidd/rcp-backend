package com.remipreparateur.medical.protocole.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** DTOs de la bibliothèque de protocoles de reprise (RTP) du club. */
public final class ProtocoleModeleDtos {

    private ProtocoleModeleDtos() {}

    public record EtapeModeleRequest(
            @NotBlank String libelle,
            Short jDebut,
            Short jFin,
            String description) {}

    /** Création / édition : les étapes sont remplacées en bloc, dans l'ordre de la liste. */
    public record ModeleRequest(
            @NotBlank String nom,
            String description,
            Boolean actif,
            List<String> typesBlessure,
            List<String> zonesCorporelles,
            List<String> gravites,
            @NotEmpty @Valid List<EtapeModeleRequest> etapes) {}

    /** Capitalisation d'un protocole en cours : nouveau modèle depuis les étapes d'une blessure. */
    public record DepuisBlessureRequest(
            @NotBlank String nom,
            String description) {}

    public record EtapeModeleResponse(
            UUID id,
            short ordre,
            String libelle,
            Short jDebut,
            Short jFin,
            String description) {}

    public record ModeleResponse(
            UUID id,
            String nom,
            String description,
            boolean actif,
            short ordre,
            List<String> typesBlessure,
            List<String> zonesCorporelles,
            List<String> gravites,
            List<EtapeModeleResponse> etapes,
            LocalDateTime createdAt) {}
}
