package com.remipreparateur.notification.dto;

import jakarta.validation.constraints.NotBlank;

/** DTOs des abonnements Web Push. */
public final class PushDtos {

    private PushDtos() {}

    /** Abonnement envoyé par le navigateur (clés issues de PushManager.subscribe). */
    public record SubscriptionRequest(
            @NotBlank String endpoint,
            @NotBlank String p256dh,
            @NotBlank String auth,
            String userAgent) {}

    public record DesabonnementRequest(@NotBlank String endpoint) {}

    /** Clé publique VAPID exposée au front + indicateur d'activation du push. */
    public record ClePubliqueDto(String publicKey, boolean actif) {}

    /** Diagnostic : nb d'abonnements (devices) de l'utilisateur courant + push actif côté serveur. */
    public record EtatAbonnementDto(long abonnements, boolean pushActifServeur) {}
}
