package com.remipreparateur.dto;

import com.remipreparateur.entity.Presence.StatutPresence;

import java.util.List;
import java.util.UUID;

/** DTOs du module Présence (feuille de présence par séance). */
public final class PresenceDtos {

    private PresenceDtos() {}

    /** Une ligne de la feuille : joueur + son statut de présence. */
    public record LignePresence(
            UUID joueurId,
            String prenom,
            String nom,
            String poste,
            StatutPresence statut,
            String note) {}

    /** La feuille complète d'une séance : toutes les lignes (effectif complet). */
    public record FeuillePresence(
            UUID seanceId,
            List<LignePresence> lignes) {}

    /** Requête de sauvegarde d'une seule ligne (PUT /api/seances/{id}/presence/{joueurId}). */
    public record SavePresence(
            StatutPresence statut,
            String note) {}

    /** Requête de sauvegarde groupée (PUT /api/seances/{id}/presence). */
    public record SaveFeuillePresence(
            List<SaveLigne> lignes) {
        public record SaveLigne(UUID joueurId, StatutPresence statut, String note) {}
    }
}
