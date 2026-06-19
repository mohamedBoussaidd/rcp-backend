package com.remipreparateur.notification.dto;

import com.remipreparateur.notification.entity.NiveauEnvoi;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** DTOs du chat 1-sens (staff→joueur, joueur autorisé→équipe/cibles). */
public final class MessageDtos {

    private MessageDtos() {}

    /**
     * Envoi d'un message. {@code destinataires} vide/null = toute l'équipe ; sinon liste de
     * fiches joueur ciblées. {@code titre} optionnel.
     */
    public record MessageRequest(
            List<UUID> destinataires,
            @Size(max = 120) String titre,
            @NotBlank String corps) {}

    /** Une ligne de l'historique des messages envoyés par l'utilisateur courant. */
    public record MessageEnvoyeDto(
            UUID threadId,
            String titre,
            String corps,
            int nbDestinataires,
            LocalDateTime createdAt) {}

    /** Capacité d'émission de l'utilisateur courant (pour afficher/masquer le chat). */
    public record CapaciteEnvoiDto(boolean peutEnvoyer, boolean staff, NiveauEnvoi niveauJoueur,
                                   boolean peutCibler) {}

    /** Destinataire possible (coéquipier) pour le ciblage. */
    public record DestinataireDto(UUID joueurId, String nom) {}
}
