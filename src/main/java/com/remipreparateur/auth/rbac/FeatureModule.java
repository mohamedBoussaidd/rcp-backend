package com.remipreparateur.auth.rbac;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Catalogue des MODULES FONCTIONNELS activables par club (couche « produit / abonnement »).
 *
 * <p>À ne pas confondre avec {@link Permission} (qui dit <em>qui</em> peut faire quoi) : un module
 * dit <em>ce que le club possède</em>, tout court. Un club amateur peut n'avoir ni GPS ni médical.
 * Quand un module est désactivé pour un club, toutes les {@link Permission} qui en dépendent sont
 * retirées à la volée (voir {@code PermissionResolver}) → les endpoints correspondants renvoient 403
 * et le front masque les écrans, sans jamais supprimer la moindre donnée.
 *
 * <p>Les modules {@link #isSocle() socle} sont TOUJOURS actifs (non désactivables) : ils forment
 * l'application minimale viable (planning, effectif, administration).
 */
public enum FeatureModule {

    // ── Socle : toujours actif, non désactivable ────────────────────────────
    PLANNING("planning", "Planning & séances",
            "Calendrier, séances, types de séance, modèles de semaine, saison", true, 0),
    EFFECTIF("effectif", "Effectif",
            "Fiches joueurs et composition de l'effectif", true, 1),
    ADMINISTRATION("administration", "Administration du club",
            "Comptes, rôles & accès, configuration du club", true, 2),

    // ── Activables : pilotés par le pack + surcharges par club ───────────────
    PRESENCE("presence", "Présence / Appel",
            "Appel des séances et historique de présence", false, 10),
    MATCH("match", "Matchs",
            "Préparation, composition et debrief de match", false, 11),
    PWA_JOUEUR("pwa_joueur", "Espace joueur (application)",
            "Application mobile joueur : calendrier, convocations, auto-déclaration", false, 12),
    WELLNESS("wellness", "Ressenti & RPE",
            "Wellness (Hooper), charge perçue sRPE et conseils au joueur", false, 13),
    PREPA_PHYSIQUE("prepa_physique", "Préparation physique",
            "Charge, ACWR, readiness, état de l'effectif et suivi du risque", false, 14),
    PESEES("pesees", "Pesées & poids",
            "Suivi du poids et écart au poids de forme", false, 15),
    GPS("gps", "GPS",
            "Import GPS, vue séance, charge d'équipe, zones de vitesse", false, 16),
    TACTIQUE("tactique", "Tactique",
            "Schémas, exercices, plan de jeu et formations", false, 17),
    DIAPORAMA("diaporama", "Diaporama",
            "Diaporamas de séance pour la TV / vidéoprojecteur", false, 18),
    MEDICAL("medical", "Médical",
            "Blessures, infirmerie, protocoles de reprise, documents médicaux et gênes", false, 19),
    NOTIFICATIONS("notifications", "Notifications",
            "Notifications in-app et push (Web Push)", false, 20),
    SUIVI_INDIVIDUEL("suivi_individuel", "Suivi individuel",
            "Axes de travail, entretiens individuels et auto-évaluations du joueur", false, 21),
    DOCUMENTS_ADMIN("documents_admin", "Licences & documents",
            "Référentiel des documents requis, conformité de l'effectif et relances", false, 22),
    SEANCES_MODELES("seances_modeles", "Bibliothèque de séances",
            "Séances-modèles réutilisables (gabarits planifiables dans le calendrier)", false, 23),
    ESPACE_STAFF("espace_staff", "Espace staff (application)",
            "Application mobile staff : agenda, appel, effectif, messagerie, documents et notifications push", false, 24),
    CONTRATS("contrats", "Contrats & paie",
            "Contrats des joueurs et du staff (échéances, PDF signés) et distribution des fiches de paye", false, 25),
    MOTEUR_TACTIQUE("moteur_tactique", "Moteur tactique",
            "Règles de positionnement collectif dérivées du plan de jeu : calibration par zones et mode dynamique du tableau tactique", false, 26),
    SEANCE_AVANCEE("seance_avancee", "Séances & exercices enrichis",
            "Mode avancé de préparation : cadre pédagogique des exercices, blocs de séance, effectifs du jour, dominantes et projet de jeu travaillé", false, 27),
    IMPORT_PHOTO_IA("import_photo_ia", "Import photo (IA)",
            "Import d'une séance ou d'un exercice depuis une photo : l'IA extrait les champs et le schéma tactique, l'entraîneur ajuste", false, 28);

    private final String code;
    private final String libelle;
    private final String description;
    private final boolean socle;
    private final int ordre;

    FeatureModule(String code, String libelle, String description, boolean socle, int ordre) {
        this.code = code;
        this.libelle = libelle;
        this.description = description;
        this.socle = socle;
        this.ordre = ordre;
    }

    public String getCode() { return code; }
    public String getLibelle() { return libelle; }
    public String getDescription() { return description; }
    public boolean isSocle() { return socle; }
    public int getOrdre() { return ordre; }

    /**
     * Modules dont au moins UN doit être actif pour que {@code this} soit cohérent.
     * Ex. : la Préparation physique n'a de sens qu'avec une source de charge (Wellness/RPE OU GPS).
     * Ensemble vide = aucune contrainte.
     */
    public Set<FeatureModule> requiertAuMoinsUn() {
        if (this == PREPA_PHYSIQUE) {
            return Set.of(WELLNESS, GPS);
        }
        return Set.of();
    }

    /**
     * Modules qui DÉVERROUILLENT une permission : la permission est accordée dès qu'AU MOINS un de
     * ces modules est actif. La plupart des permissions dépendent d'un seul module, mais certaines
     * sont partagées — ex. {@code predictions:read} (charge/IA) sert à la fois à la Préparation
     * physique ET aux écrans GPS. Un module socle (toujours actif) garantit l'accès.
     */
    public static Set<FeatureModule> modulesDe(Permission p) {
        if (p == Permission.PREDICTIONS_READ) {
            return Set.of(PREPA_PHYSIQUE, GPS);
        }
        if (p == Permission.COACHING_ACCESS) {
            // Accès à l'espace Coaching = conteneur multi-modules (tactique / match / diaporama) PLUS la
            // bibliothèque de séances (socle Planning). En incluant le socle PLANNING (toujours actif),
            // la clé survit TOUJOURS au filtrage pack : le menu Coaching reste ouvert et chaque
            // sous-écran se gate ensuite sur son propre module.
            return Set.of(PLANNING, TACTIQUE, MATCH, DIAPORAMA);
        }
        return Set.of(of(p));
    }

    // ── Mapping Permission → module qui la gouverne ──────────────────────────
    // Reflète le rangement produit : wellness:read = Prépa (carburant de la charge),
    // wellness:treat/reopen = Médical (gestion d'une gêne), predictions:read = Prépa physique.
    public static FeatureModule of(Permission p) {
        return switch (p) {
            case SEANCES_READ, SEANCES_WRITE, TYPESEANCES_WRITE, SAISON_READ, SAISON_MANAGE -> PLANNING;
            case SEANCE_AVANCEE_ACCESS -> SEANCE_AVANCEE;
            case IMPORT_PHOTO_USE -> IMPORT_PHOTO_IA;
            case JOUEURS_READ, JOUEURS_WRITE -> EFFECTIF;
            case CONFIGURATION_READ, CONFIGURATION_WRITE, MEMBRES_MANAGE, CLUB_MANAGE -> ADMINISTRATION;
            case PRESENCE_WRITE -> PRESENCE;
            case MATCHS_READ, MATCHS_WRITE -> MATCH;
            case WELLNESS_READ, CONSEILS_READ, CONSEILS_WRITE -> WELLNESS;
            case PREDICTIONS_READ -> PREPA_PHYSIQUE;
            case PESEES_READ, PESEES_WRITE -> PESEES;
            case GPS_IMPORT -> GPS;
            case COACHING_ACCESS, EXERCICES_READ, EXERCICES_WRITE, FORMATIONS_READ, FORMATIONS_WRITE,
                 SCHEMAS_READ, SCHEMAS_WRITE, PLANDEJEU_READ, PLANDEJEU_WRITE -> TACTIQUE;
            case REGLES_TACTIQUES_READ, REGLES_TACTIQUES_WRITE -> MOTEUR_TACTIQUE;
            case SEANCES_MODELES_ACCESS -> SEANCES_MODELES;
            case ESPACE_STAFF_ACCESS -> ESPACE_STAFF;
            case CONTRATS_MANAGE -> CONTRATS;
            case DIAPORAMA_READ, DIAPORAMA_WRITE, DIAPORAMA_MANAGE -> DIAPORAMA;
            case BLESSURES_READ, BLESSURES_WRITE, BLESSURES_QUALIFY, DOCUMENTS_READ, DOCUMENTS_WRITE,
                 WELLNESS_TREAT, WELLNESS_REOPEN -> MEDICAL;
            case NOTIFICATIONS_CONFIG -> NOTIFICATIONS;
            case ENTRETIEN_READ, ENTRETIEN_WRITE, ENTRETIEN_MANAGE, AXE_READ, AXE_WRITE -> SUIVI_INDIVIDUEL;
            case DOCADMIN_CONFIGURE, DOCADMIN_READ, DOCADMIN_VALIDATE, DOCADMIN_UPLOAD -> DOCUMENTS_ADMIN;
        };
    }

    private static final Map<String, FeatureModule> PAR_CODE =
            Arrays.stream(values()).collect(Collectors.toMap(FeatureModule::getCode, Function.identity()));

    public static FeatureModule parCode(String code) {
        return PAR_CODE.get(code);
    }

    /** Codes des modules socle (toujours actifs). */
    public static Set<String> socleCodes() {
        return Arrays.stream(values()).filter(FeatureModule::isSocle)
                .map(FeatureModule::getCode).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Codes des modules activables (pilotés par pack + surcharges). */
    public static Set<String> activableCodes() {
        return Arrays.stream(values()).filter(m -> !m.isSocle())
                .map(FeatureModule::getCode).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Tous les codes (socle + activables), ordonnés. */
    public static List<String> tousCodes() {
        return Arrays.stream(values()).sorted((a, b) -> Integer.compare(a.ordre, b.ordre))
                .map(FeatureModule::getCode).toList();
    }
}
