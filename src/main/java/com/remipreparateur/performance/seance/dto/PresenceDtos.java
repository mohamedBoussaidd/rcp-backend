package com.remipreparateur.performance.seance.dto;

import com.remipreparateur.performance.seance.entity.Presence.StatutPresence;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** DTOs du module Présence (feuille d'appel par séance + assiduité par joueur). */
public final class PresenceDtos {

    private PresenceDtos() {}

    /**
     * Une ligne de la feuille d'appel : joueur + son statut de présence.
     * {@code blesse} est dérivé du statut administratif du joueur (médical) : un joueur blessé
     * est affiché comme tel et n'est pas comptabilisé comme une absence.
     * {@code source} = STAFF (appel) ou JOUEUR (auto-déclaration PWA), null si non renseigné.
     */
    public record LignePresence(
            UUID joueurId,
            String prenom,
            String nom,
            String poste,
            StatutPresence statut,
            String note,
            boolean blesse,
            String source) {}

    /** La feuille complète d'une séance : toutes les lignes (effectif complet). */
    public record FeuillePresence(
            UUID seanceId,
            List<LignePresence> lignes) {}

    /**
     * Résumé chiffré de l'appel d'une séance (pour dashboard / pastille « X/Y dispo »).
     * {@code dispo} = attendus présents = effectif − blessés − absents − excusés (les retards
     * restent comptés comme présents). {@code presents} = dispo − retards.
     */
    public record ResumeAppel(
            UUID seanceId,
            int effectif,
            int presents,
            int blesses,
            int absents,
            int excuses,
            int retards,
            int dispo) {}

    /** Requête de sauvegarde d'une seule ligne (PUT /api/seances/{id}/presence/{joueurId}). */
    public record SavePresence(
            StatutPresence statut,
            String note) {}

    /** Requête de sauvegarde groupée (PUT /api/seances/{id}/presence). */
    public record SaveFeuillePresence(
            List<SaveLigne> lignes) {
        public record SaveLigne(UUID joueurId, StatutPresence statut, String note) {}
    }

    /** Auto-déclaration de présence par le joueur depuis la PWA (POST /api/moi/seances/{id}/presence). */
    public record DeclarationPresence(
            StatutPresence statut,
            String commentaire) {}

    /** Ce que le joueur a déjà déclaré pour une séance (GET /api/moi/presences), pour pré-remplir la PWA. */
    public record MaDeclaration(
            UUID seanceId,
            StatutPresence statut,
            String note) {}

    // ──────────────────────────── Assiduité (agrégat par joueur) ────────────────────────────

    /** Une absence/retard/excuse passé(e), pour l'historique (déclinaisons de présence). */
    public record EvenementAssiduite(
            UUID seanceId,
            LocalDate date,
            String titre,
            StatutPresence statut,
            String note,
            String source) {}

    /** Résumé léger d'assiduité par joueur (pour la colonne triable de l'effectif). */
    public record AssiduiteResume(
            UUID joueurId,
            int taux,
            int absents,
            int retards,
            int excuses,
            int recents) {}

    /**
     * Bilan d'assiduité d'un joueur sur une saison (entraînements uniquement, hors matchs).
     * {@code taux} = présences / séances comptabilisées (0–100). {@code recents} = nombre
     * d'absences+excuses+retards sur les 14 derniers jours (signal de décrochage).
     */
    public record AssiduiteJoueur(
            UUID joueurId,
            UUID saisonId,
            String saisonLibelle,
            int nbSeances,
            int presents,
            int absents,
            int excuses,
            int retards,
            int taux,
            int recents,
            List<EvenementAssiduite> historique) {}

    // ──────────────────────── Historique de présence (page dédiée) ───────────────────────

    /**
     * Une ligne du tableau d'historique en mode <b>Équipe</b> : un entraînement de la fenêtre avec
     * ses compteurs de présence. {@code dispo} = effectif − blessés − absents − excusés (les retards
     * restent comptés comme présents). {@code taux} = présents / (effectif − blessés), les blessés
     * étant neutres (cohérent avec l'assiduité par joueur). {@code declaresJoueur} = nombre de lignes
     * auto-déclarées par les joueurs depuis la PWA (indicateur de remontée terrain).
     */
    public record LigneHistoriqueSeance(
            UUID seanceId,
            LocalDate date,
            String titre,
            String type,
            int effectif,
            int presents,
            int blesses,
            int absents,
            int excuses,
            int retards,
            int dispo,
            int declaresJoueur,
            int taux) {}

    /**
     * Réponse de l'historique en mode <b>Équipe</b> : la fenêtre effectivement résolue (saison ou
     * période libre) + une ligne par entraînement comptabilisé, du plus récent au plus ancien.
     */
    public record HistoriqueEquipe(
            UUID saisonId,
            String saisonLibelle,
            LocalDate du,
            LocalDate au,
            List<LigneHistoriqueSeance> seances) {}
}
