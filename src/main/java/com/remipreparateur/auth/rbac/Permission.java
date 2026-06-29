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

    // ── Analyse / GPS ───────────────────────────────────────────────
    PREDICTIONS_READ("predictions:read", "Analyse / GPS", "Voir charge & prédictions IA"),
    GPS_IMPORT("gps:import", "Analyse / GPS", "Importer les données GPS"),

    // ── Effectif ────────────────────────────────────────────────────
    JOUEURS_READ("joueurs:read", "Effectif", "Voir les fiches joueurs"),
    JOUEURS_WRITE("joueurs:write", "Effectif", "Créer / éditer les fiches joueurs"),
    PESEES_READ("pesees:read", "Effectif", "Voir les pesées"),
    PESEES_WRITE("pesees:write", "Effectif", "Saisir les pesées"),

    // ── Médical ─────────────────────────────────────────────────────
    BLESSURES_READ("blessures:read", "Médical", "Voir les blessures"),
    BLESSURES_WRITE("blessures:write", "Médical", "Créer / éditer les blessures"),
    DOCUMENTS_READ("documents:read", "Médical", "Voir les documents médicaux"),
    DOCUMENTS_WRITE("documents:write", "Médical", "Déposer / supprimer des documents"),
    WELLNESS_READ("wellness:read", "Médical", "Voir wellness & RPE"),
    WELLNESS_TREAT("wellness:treat", "Médical", "Traiter une gêne"),
    WELLNESS_REOPEN("wellness:reopen", "Médical", "Rouvrir une gêne"),
    CONSEILS_READ("conseils:read", "Médical", "Voir les conseils"),
    CONSEILS_WRITE("conseils:write", "Médical", "Écrire des conseils au joueur"),

    // ── Tactique & Match ────────────────────────────────────────────
    EXERCICES_READ("exercices:read", "Tactique & Match", "Voir les exercices"),
    EXERCICES_WRITE("exercices:write", "Tactique & Match", "Éditer la bibliothèque d'exercices"),
    FORMATIONS_READ("formations:read", "Tactique & Match", "Voir les formations"),
    FORMATIONS_WRITE("formations:write", "Tactique & Match", "Éditer les formations"),
    SCHEMAS_READ("schemas:read", "Tactique & Match", "Voir les schémas"),
    SCHEMAS_WRITE("schemas:write", "Tactique & Match", "Éditer les schémas tactiques"),
    PLANDEJEU_READ("plandejeu:read", "Tactique & Match", "Voir le plan de jeu"),
    PLANDEJEU_WRITE("plandejeu:write", "Tactique & Match", "Éditer le plan de jeu"),
    MATCHS_READ("matchs:read", "Tactique & Match", "Voir les matchs"),
    MATCHS_WRITE("matchs:write", "Tactique & Match", "Gérer les matchs"),
    DIAPORAMA_READ("diaporama:read", "Tactique & Match", "Voir les diaporamas"),
    DIAPORAMA_WRITE("diaporama:write", "Tactique & Match", "Créer / éditer ses diaporamas"),
    DIAPORAMA_MANAGE("diaporama:manage", "Tactique & Match", "Supprimer / modérer toute diapo du club"),

    // ── Paramètres & Notifications ──────────────────────────────────
    CONFIGURATION_READ("configuration:read", "Paramètres & Notifications", "Voir la configuration"),
    CONFIGURATION_WRITE("configuration:write", "Paramètres & Notifications", "Modifier la configuration"),
    NOTIFICATIONS_CONFIG("notifications:config", "Paramètres & Notifications", "Configurer les notifications"),

    // ── Saison ──────────────────────────────────────────────────────
    SAISON_READ("saison:read", "Saison", "Voir la saison et les périodes"),
    SAISON_MANAGE("saison:manage", "Saison", "Ouvrir / clôturer la saison, gérer les périodes et l'effectif"),

    // ── Gestion du club ─────────────────────────────────────────────
    MEMBRES_MANAGE("membres:manage", "Gestion du club", "Gérer les comptes (staff & joueurs) de son périmètre"),
    CLUB_MANAGE("club:manage", "Gestion du club", "Gérer le club (équipes, rôles, tous les membres)");

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
