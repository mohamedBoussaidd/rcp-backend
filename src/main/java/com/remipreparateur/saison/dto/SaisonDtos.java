package com.remipreparateur.saison.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** DTOs de la saison, des périodes typées et de l'effectif de saison. */
public class SaisonDtos {

    /** Période typée (entrée et sortie). */
    public record PeriodeDto(
            UUID id,
            String type,            // PREPARATION|COMPETITION|TREVE|REPRISE|INTERSAISON
            String libelle,
            LocalDate dateDebut,
            LocalDate dateFin,
            short ordre
    ) {}

    /**
     * Saison (niveau CLUB) avec, pour l'ÉQUIPE de scope, ses périodes + la période courante
     * (selon la date du jour) et l'effectif. {@code equipeId} = équipe à laquelle se rapportent
     * périodes/effectif (null si non résolue, ex. président sans équipe sélectionnée).
     */
    public record SaisonDto(
            UUID id,
            UUID clubId,
            UUID equipeId,
            String libelle,
            LocalDate dateDebut,
            LocalDate dateFin,
            String statut,
            PeriodeDto periodeCourante,    // null hors de toute période
            List<PeriodeDto> periodes,
            int effectifCount
    ) {}

    /** Création / ouverture d'une saison. */
    public record SaisonRequest(
            String libelle,
            LocalDate dateDebut,
            LocalDate dateFin,
            String statut,                 // optionnel (défaut EN_COURS)
            boolean genererPeriodes        // true = générer les périodes par défaut depuis les dates
    ) {}

    /** Remplacement complet des périodes d'une saison. */
    public record PeriodesRequest(
            List<PeriodeDto> periodes
    ) {}

    /** Membre de l'effectif d'une saison (lecture). */
    public record EffectifMembreDto(
            UUID joueurId,
            String nom,
            String prenom,
            String poste,
            String statut,                 // dispo actuelle du joueur
            Short numeroMaillot,
            LocalDate dateEntree,
            LocalDate dateSortie
    ) {}

    /** Définition de l'effectif d'une saison (liste de joueurs retenus). */
    public record EffectifRequest(
            List<UUID> joueurIds
    ) {}

    /** Une ligne de proposition de reconduction (joueur de la saison précédente). */
    public record ReconductionLigne(
            UUID joueurId,
            String nom,
            String prenom,
            String poste,
            boolean suggerer,              // proposé coché par défaut (décocher = transfert)
            boolean blesse                 // pour info : blessure encore active (traverse la saison)
    ) {}

    /** Proposition de reconduction d'effectif à l'ouverture d'une saison. */
    public record ReconductionProposition(
            UUID saisonPrecedenteId,
            String saisonPrecedenteLibelle,
            List<ReconductionLigne> lignes
    ) {}

    /**
     * Résultat de l'application d'une reconduction : effectif final + comptes joueur dont
     * l'accès PWA a été coupé (joueurs écartés) ou rouvert (joueurs réintégrés).
     */
    public record ReconductionResultat(
            List<EffectifMembreDto> effectif,
            int comptesDesactives,
            int comptesReactives
    ) {}

    /** Bilan synthétique d'une saison (pour la comparaison inter-saisons). */
    public record BilanSaison(
            UUID saisonId,
            String libelle,
            String statut,
            LocalDate dateDebut,
            LocalDate dateFin,
            int jours,
            int effectifCount,
            int nbSeances,
            int nbBlessures
    ) {}
}
