package com.remipreparateur.notification.dto;

import com.remipreparateur.notification.entity.EmetteurType;
import com.remipreparateur.notification.entity.Priorite;
import com.remipreparateur.notification.entity.TypeNotification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** DTOs de lecture/écriture des notifications in-app. */
public final class NotificationDtos {

    private NotificationDtos() {}

    /** Une notification telle qu'affichée au destinataire. */
    public record NotificationResponse(
            UUID id,
            TypeNotification type,
            String categorie,
            String titre,
            String corps,
            String lien,
            Priorite priorite,
            EmetteurType emetteurType,
            String emetteurNom,
            UUID sujetJoueurId,
            String sujetJoueurNom,
            UUID threadId,
            boolean repondable,
            boolean lu,
            LocalDateTime createdAt) {}

    /** Page de notifications + compteur de non-lus (pour la cloche). */
    public record NotificationPage(
            List<NotificationResponse> items,
            long nonLus,
            int page,
            boolean dernierePage) {}

    /** Réponse légère pour le polling de la cloche. */
    public record CompteurResponse(long nonLus) {}
}
