package com.remipreparateur.medical.conseil.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

/** DTOs des conseils du staff (médical / préparateur) au joueur. */
public final class ConseilDtos {

    private ConseilDtos() {}

    /**
     * Création / modification d'un conseil. {@code joueurId} null = conseil d'équipe
     * (commun) ; sinon conseil personnel à ce joueur.
     */
    public record ConseilRequest(
            UUID joueurId,
            @NotBlank @Size(max = 120) String titre,
            @NotBlank String texte,
            @Size(max = 40) String icone) {}

    public record ConseilResponse(
            UUID id,
            UUID equipeId,
            UUID joueurId,
            String joueurNom,
            String joueurPrenom,
            String titre,
            String texte,
            String icone,
            /** true si conseil d'équipe (joueurId null), false si personnel. */
            boolean equipe,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}
}
