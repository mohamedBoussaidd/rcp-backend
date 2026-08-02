package com.remipreparateur.auth.rbac;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Catalogue FIGÉ des permissions applicatives (capabilities) au format {@code module:action}.
 *
 * <p>Source de vérité unique du « vocabulaire » d'autorisation : le backend autorise sur ces
 * permissions ({@code hasAuthority("seances:write")}), plus jamais sur un nom de rôle. Les rôles
 * (système ou custom) ne sont que des paquets de ces permissions, stockés en base. Ajouter une
 * capability = ajouter une valeur ici + la règle {@code hasAuthority} sur l'endpoint concerné.
 *
 * <p>Le rôle JOUEUR n'utilise AUCUNE de ces permissions : son accès est un self-scope par token
 * via {@code /api/moi/**}. SUPER_ADMIN est un bypass « dieu » hors de ce catalogue.
 */
public enum Permission {

    // ── Entraînement ────────────────────────────────────────────────
    SEANCES_READ("seances:read", "Entraînement", "Voir les séances"),
    SEANCES_WRITE("seances:write", "Entraînement", "Créer / éditer les séances"),
    PRESENCE_WRITE("presence:write", "Entraînement", "Saisir la présence"),
    TYPESEANCES_WRITE("typeseances:write", "Entraînement", "Paramétrer les types de séance"),
    SEANCE_AVANCEE_ACCESS("seance_avancee:access", "Entraînement",
            "Éditer le mode avancé des séances et exercices (pédagogie, blocs, effectifs du jour)"),
    IMPORT_PHOTO_USE("import_photo:use", "Entraînement",
            "Importer une séance ou un exercice depuis une photo (IA vision)"),
    SEANCE_IA_GENERATE("seance_ia:generate", "Entraînement",
            "Générer une séance par IA (à partir d'une demande en langage naturel)"),

    // ── Analyse / GPS ───────────────────────────────────────────────
    PREDICTIONS_READ("predictions:read", "Analyse / GPS", "Voir charge & prédictions IA"),
    PREDICTIONS_WRITE("predictions:write", "Analyse / GPS", "Définir l'objectif hebdomadaire de charge"),
    PREPA_IA_BRIEFING("prepa_ia:briefing", "Analyse / GPS", "Voir le briefing IA du préparateur (note de l'équipe)"),
    PREPA_IA_DEBRIEF("prepa_ia:debrief", "Analyse / GPS", "Générer le debrief IA d'une séance"),
    PREPA_IA_DERIVES("prepa_ia:derives", "Analyse / GPS", "Voir les dérives de charge et l'alerte de surveillance"),
    PREPA_IA_SIMULATION("prepa_ia:simulation", "Analyse / GPS",
            "Simuler une séance à venir et son impact sur la charge (« et si… »)"),
    GPS_IMPORT("gps:import", "Analyse / GPS", "Importer les données GPS"),
    RPE_IMPORT("rpe:import", "Analyse / GPS", "Importer le RPE / ressenti des joueurs (fichier)"),

    // ── Effectif ────────────────────────────────────────────────────
    JOUEURS_READ("joueurs:read", "Effectif", "Voir les fiches joueurs"),
    JOUEURS_WRITE("joueurs:write", "Effectif", "Créer / éditer les fiches joueurs"),
    PESEES_READ("pesees:read", "Effectif", "Voir les pesées"),
    PESEES_WRITE("pesees:write", "Effectif", "Saisir les pesées"),

    // ── Médical ─────────────────────────────────────────────────────
    BLESSURES_READ("blessures:read", "Médical", "Voir les blessures"),
    BLESSURES_WRITE("blessures:write", "Médical", "Créer / éditer les blessures"),
    BLESSURES_QUALIFY("blessures:qualify", "Médical", "Qualifier arrêt / accident de travail et déposer la déclaration"),
    DOCUMENTS_READ("documents:read", "Médical", "Voir les documents médicaux"),
    DOCUMENTS_WRITE("documents:write", "Médical", "Déposer / supprimer des documents"),
    WELLNESS_READ("wellness:read", "Médical", "Voir wellness & RPE"),
    WELLNESS_TREAT("wellness:treat", "Médical", "Traiter une gêne"),
    WELLNESS_REOPEN("wellness:reopen", "Médical", "Rouvrir une gêne"),
    HOOPER_IMPORT("hooper:import", "Médical", "Importer le ressenti quotidien / wellness des joueurs (fichier)"),
    CONSEILS_READ("conseils:read", "Médical", "Voir les conseils"),
    CONSEILS_WRITE("conseils:write", "Médical", "Écrire des conseils au joueur"),

    // ── Tactique & Match ────────────────────────────────────────────
    COACHING_ACCESS("coaching:access", "Tactique & Match", "Accéder à l'espace Coaching"),
    SEANCES_MODELES_ACCESS("seances_modeles:access", "Tactique & Match", "Accéder à la bibliothèque de séances-modèles"),
    EXERCICES_READ("exercices:read", "Tactique & Match", "Voir les exercices"),
    EXERCICES_WRITE("exercices:write", "Tactique & Match", "Éditer la bibliothèque d'exercices"),
    FORMATIONS_READ("formations:read", "Tactique & Match", "Voir les formations"),
    FORMATIONS_WRITE("formations:write", "Tactique & Match", "Éditer les formations"),
    SCHEMAS_READ("schemas:read", "Tactique & Match", "Voir les schémas"),
    SCHEMAS_WRITE("schemas:write", "Tactique & Match", "Éditer les schémas tactiques"),
    PLANDEJEU_READ("plandejeu:read", "Tactique & Match", "Voir le plan de jeu"),
    PLANDEJEU_WRITE("plandejeu:write", "Tactique & Match", "Éditer le plan de jeu"),
    REGLES_TACTIQUES_READ("regles_tactiques:read", "Tactique & Match", "Voir les règles de jeu (moteur tactique)"),
    REGLES_TACTIQUES_WRITE("regles_tactiques:write", "Tactique & Match", "Calibrer les règles de jeu (moteur tactique)"),
    MATCHS_READ("matchs:read", "Tactique & Match", "Voir les matchs"),
    MATCHS_WRITE("matchs:write", "Tactique & Match", "Gérer les matchs"),
    STATS_READ("stats:read", "Tactique & Match",
            "Voir les statistiques de compétition d'un joueur (temps de jeu, participation, buts, cartons)"),
    STATS_WRITE("stats:write", "Tactique & Match",
            "Remplir la feuille de match : minutes jouées, buteurs, passeurs, cartons"),
    DIAPORAMA_READ("diaporama:read", "Tactique & Match", "Voir les diaporamas"),
    DIAPORAMA_WRITE("diaporama:write", "Tactique & Match", "Créer / éditer ses diaporamas"),
    DIAPORAMA_MANAGE("diaporama:manage", "Tactique & Match", "Supprimer / modérer toute diapo du club"),

    // ── Suivi individuel ────────────────────────────────────────────
    ENTRETIEN_READ("entretien:read", "Suivi individuel", "Voir les entretiens & la progression"),
    ENTRETIEN_WRITE("entretien:write", "Suivi individuel", "Mener / éditer ses entretiens"),
    ENTRETIEN_MANAGE("entretien:manage", "Suivi individuel", "Supprimer / modérer les entretiens du club"),
    AXE_READ("axe:read", "Suivi individuel", "Voir les axes de travail"),
    AXE_WRITE("axe:write", "Suivi individuel", "Créer / éditer les axes de travail"),
    SUIVI_COACH_READ("suivi_coach:read", "Suivi individuel",
            "Voir le fil de vie du joueur, ses objectifs individuels et les notes du staff"),
    SUIVI_COACH_WRITE("suivi_coach:write", "Suivi individuel",
            "Fixer les objectifs individuels et écrire les notes du staff sur un joueur"),

    // ── Administration du club ──────────────────────────────────────
    DOCADMIN_CONFIGURE("docadmin:configure", "Administration du club", "Gérer le référentiel des documents requis"),
    DOCADMIN_READ("docadmin:read", "Administration du club", "Voir la conformité documentaire de l'effectif"),
    DOCADMIN_VALIDATE("docadmin:validate", "Administration du club", "Valider / refuser un document déposé"),
    DOCADMIN_UPLOAD("docadmin:upload", "Administration du club", "Déposer un document pour un joueur"),
    CONTRATS_MANAGE("contrats:manage", "Administration du club", "Gérer les contrats et distribuer les fiches de paye"),

    // ── Paramètres & Notifications ──────────────────────────────────
    CONFIGURATION_READ("configuration:read", "Paramètres & Notifications", "Voir la configuration"),
    CONFIGURATION_WRITE("configuration:write", "Paramètres & Notifications", "Modifier la configuration"),
    NOTIFICATIONS_CONFIG("notifications:config", "Paramètres & Notifications", "Configurer les notifications"),

    // ── Saison ──────────────────────────────────────────────────────
    SAISON_READ("saison:read", "Saison", "Voir la saison et les périodes"),
    SAISON_MANAGE("saison:manage", "Saison", "Ouvrir / clôturer la saison, gérer les périodes et l'effectif"),

    // ── Gestion du club ─────────────────────────────────────────────
    MEMBRES_MANAGE("membres:manage", "Gestion du club", "Gérer les comptes (staff & joueurs) de son périmètre"),
    CLUB_MANAGE("club:manage", "Gestion du club", "Gérer le club (équipes, rôles, tous les membres)"),

    // ── Espace staff (application mobile) ───────────────────────────
    ESPACE_STAFF_ACCESS("espace_staff:access", "Espace staff", "Accéder à l'application mobile staff"),

    // ── Assistant IA ────────────────────────────────────────────────
    // Volontairement HORS du groupe « Analyse / GPS » (contrairement aux cartes prepa_ia:*) : le chat
    // est transverse. Le contexte qu'il reçoit est assemblé à partir des permissions de l'utilisateur
    // (voir ContexteChat) — un médecin n'aura jamais les données de charge dans son prompt, et
    // réciproquement. Ouvrir le chat à un nouveau métier = ajouter un ContexteChat, pas une permission.
    ASSISTANT_IA_CHAT("assistant_ia:chat", "Assistant IA",
            "Discuter avec l'assistant IA (contexte limité aux données déjà autorisées)");

    private final String code;
    private final String module;
    private final String libelle;

    Permission(String code, String module, String libelle) {
        this.code = code;
        this.module = module;
        this.libelle = libelle;
    }

    /** Chaîne d'autorité utilisée par Spring Security et stockée en base (ex. {@code seances:write}). */
    public String getCode() {
        return code;
    }

    /** Regroupement fonctionnel (pour l'affichage en matrice). */
    public String getModule() {
        return module;
    }

    /** Libellé lisible (FR) pour l'UI. */
    public String getLibelle() {
        return libelle;
    }

    private static final Map<String, Permission> PAR_CODE =
            Arrays.stream(values()).collect(Collectors.toMap(Permission::getCode, Function.identity()));

    /** Retrouve une permission par son code, ou {@code null} si inconnu. */
    public static Permission parCode(String code) {
        return PAR_CODE.get(code);
    }
}
