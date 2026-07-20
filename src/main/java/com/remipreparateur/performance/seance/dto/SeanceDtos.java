package com.remipreparateur.performance.seance.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** DTOs du contenu d'une séance : ses exercices (référence + overrides) et leurs agrégats. */
public final class SeanceDtos {

    private SeanceDtos() {}

    /** Une ligne d'exercice envoyée par le client. Overrides null = valeur par défaut de l'exercice.
     *  {@code blocIndex} (mode avancé) = index dans la liste de blocs du même payload, null = hors bloc. */
    public record LigneRequest(
            UUID exerciceId,
            Short dureeMinutes,
            Short intensite,
            Integer distanceAttendueM,
            Integer distanceHauteIntensiteM,
            Short nbSprints,
            Integer blocIndex) {}

    /** Remplacement complet des exercices d'une séance (ordre = ordre de la liste). */
    public record ExercicesRequest(List<LigneRequest> exercices) {}

    /** Une ligne d'exercice telle qu'affichée : valeurs effectives (override sinon défaut) + libellés. */
    public record ExerciceLigne(
            UUID exerciceId,
            String nom,
            String categorie,
            String type,
            int ordre,
            Short dureeMinutes,
            Short intensite,
            String objectif,
            String description,
            String schemaJson,
            Integer distanceAttendueM,
            Integer distanceHauteIntensiteM,
            Short nbSprints,
            UUID blocId) {}

    // ══════════ Mode avancé : blocs, groupes, dominantes, sous-principes ══════════

    /**
     * Référence affichable d'un membre du staff. {@code role} et {@code equipe} sont là pour
     * DISTINGUER des homonymes : un même intervenant a souvent un compte par équipe (même
     * prénom/nom), et sans ces deux champs le sélecteur affiche des boutons identiques.
     * {@code equipe} vaut null pour un compte rattaché au club seul (Président, Administratif).
     */
    public record StaffRef(UUID id, String nom, String role, String equipe) {

        public static StaffRef de(UUID id, String prenom, String nom,
                                  com.remipreparateur.auth.entity.Role role, String equipe) {
            String complet = ((prenom != null ? prenom + " " : "") + (nom != null ? nom : "")).trim();
            return new StaffRef(id, complet, libelleRole(role), equipe);
        }

        private static String libelleRole(com.remipreparateur.auth.entity.Role r) {
            if (r == null) return null;
            return switch (r) {
                case PRESIDENT -> "Président";
                case ENTRAINEUR -> "Entraîneur";
                case PREPARATEUR -> "Préparateur";
                case MEDICAL -> "Médical";
                case ADMINISTRATIF -> "Administratif";
                default -> null;
            };
        }
    }
    public record JoueurRef(UUID id, String nom, String prenom) {}

    public record BlocDto(
            UUID id,
            int ordre,
            String libelle,
            String sequencage,
            Short dureeMinutes,
            String zoneTerrain,
            List<StaffRef> staff) {}

    /** Groupe du jour stocké (COULEUR / LIBRE). {@code blocId} null = toute la séance. */
    public record GroupeDto(
            UUID id,
            UUID blocId,
            String type,
            String libelle,
            String couleur,
            int ordre,
            List<JoueurRef> joueurs) {}

    public record BlocRequest(
            String libelle,
            String sequencage,
            Short dureeMinutes,
            String zoneTerrain,
            List<UUID> staffIds) {}

    /** {@code blocIndex} = index dans la liste de blocs du payload, null = groupe global séance. */
    public record GroupeRequest(
            Integer blocIndex,
            String type,
            String libelle,
            String couleur,
            List<UUID> joueurIds) {}

    /** Remplacement complet du contenu avancé (blocs + lignes + groupes + sélections). */
    public record ContenuAvanceRequest(
            List<BlocRequest> blocs,
            List<LigneRequest> exercices,
            List<GroupeRequest> groupes,
            List<UUID> dominanteIds,
            List<UUID> sousPrincipeIds) {}

    /**
     * Contenu d'une séance : ses exercices + agrégats + (mode avancé) blocs, groupes et
     * sélections de référentiels. Les totaux servent à pré-remplir l'objectif d'équipe.
     */
    public record ContenuSeance(
            UUID seanceId,
            List<ExerciceLigne> exercices,
            int dureeTotaleMinutes,
            Double intensiteMoyenne,
            Integer distanceTotaleAttendueM,
            Integer distanceHauteIntensiteTotaleM,
            Integer nbSprintsTotal,
            List<BlocDto> blocs,
            List<GroupeDto> groupes,
            List<UUID> dominanteIds,
            List<UUID> sousPrincipeIds) {}

    // ══════════ Fiche séance (résumé) + périodisation + groupes auto ══════════

    /** Position de la séance par rapport aux matchs de l'équipe (fenêtre ±10 jours). */
    public record PerimatchDto(
            Integer jRelatif,        // ex. +2 (après match) ou -1 (veille de match), null si aucun match proche
            String libelle,          // ex. « J+2 · après FC Annecy (2-1) »
            LocalDate dateMatch,
            String adversaire,
            String scoreMatch,
            boolean prochain) {}     // true = le match de référence est à venir

    /** Groupes calculés à la volée (jamais stockés) : médical, RTP, et le reste de l'effectif. */
    public record GroupesAutoDto(
            List<JoueurRef> blesses,
            List<JoueurRef> reathletisation,
            List<JoueurRef> disponibles) {}

    public record RefItem(String code, String libelle, String groupe) {}

    public record ObjectifsPedagogiques(
            String tactiqueOrg,
            String tactiqueFonc,
            String mental,
            String technique,
            String athletique) {}

    public record BlocResume(BlocDto bloc, List<ExerciceLigne> exercices) {}

    /** Fiche complète d'une séance (écran de validation / consultation / impression). */
    public record ResumeSeance(
            UUID seanceId,
            String titre,
            String statut,
            LocalDate date,
            LocalTime heureDebut,
            Short dureeMinutes,
            Short dureeEffectiveMinutes,
            String terrain,
            String responsable,
            String typeCode,
            String typeLibelle,
            String equipeNom,
            PerimatchDto perimatch,
            List<RefItem> dominantes,      // groupe = famille (SEANCE / ATHLETIQUE)
            List<RefItem> sousPrincipes,   // groupe = phase (OFF / DEF / T_OD / T_DO / CPA_*)
            ObjectifsPedagogiques objectifs,
            Integer objectifDistanceM,
            Integer objectifDistanceHauteIntensiteM,
            Short objectifIntensite,
            List<BlocResume> blocs,
            List<ExerciceLigne> exercicesSansBloc,
            List<GroupeDto> groupes,
            GroupesAutoDto groupesAuto,
            List<JoueurRef> absents) {}    // depuis l'appel (vide si pas d'appel)

    // ══════════ Vue joueur (PWA) : version filtrée côté serveur ══════════

    public record BlocJoueur(
            String libelle,
            String sequencage,
            Short dureeMinutes,
            String zoneTerrain,
            List<ExerciceJoueur> exercices) {}

    public record ExerciceJoueur(String nom, Short dureeMinutes, String schemaJson) {}

    public record MonGroupe(String libelle, String couleur, String blocLibelle, List<String> coequipiers) {}

    /** Fiche séance côté joueur : horaire, lieu, déroulé et SON groupe — sans objectifs,
     *  dominantes, projet de jeu ni affectation du staff (filtré serveur). */
    public record FicheSeanceJoueur(
            UUID seanceId,
            String titre,
            LocalDate date,
            LocalTime heureDebut,
            Short dureeMinutes,
            String terrain,
            String typeLibelle,
            List<BlocJoueur> blocs,
            List<ExerciceJoueur> exercicesSansBloc,
            List<MonGroupe> mesGroupes) {}

    /** Données GPS d'un joueur pour une séance, à plat (évite la sérialisation du proxy lazy joueur). */
    public record DonneeGpsDto(
            UUID joueurId,
            Short dureeMinutes,
            java.math.BigDecimal distanceTotaleM,
            java.math.BigDecimal distance15kmhM,
            java.math.BigDecimal distance19kmhM,
            java.math.BigDecimal distanceSprint24kmhM,
            java.math.BigDecimal distanceSprint28kmhM,
            Short nbSprints24kmh,
            java.math.BigDecimal vitesseMaxKmh,
            Short nbAccelerations,
            Short nbFreinages,
            java.math.BigDecimal ratioDistanceMin) {}
}
