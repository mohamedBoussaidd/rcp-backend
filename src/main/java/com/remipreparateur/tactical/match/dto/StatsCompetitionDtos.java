package com.remipreparateur.tactical.match.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Vue « compétition » d'un joueur : ce qu'un entraîneur vient chercher sur une fiche joueur —
 * combien il a joué, à quel poste, ce qu'il a produit, et ce que ça pèse face à sa charge
 * d'entraînement.
 *
 * <p>Tout est calculé sur la portée déjà résolue (équipe active) et sur la saison demandée.
 */
public final class StatsCompetitionDtos {

    private StatsCompetitionDtos() {}

    /** Une rencontre, du point de vue d'un joueur. */
    public record MatchJoueur(
            UUID matchId,
            LocalDate date,
            String adversaire,
            String competition,
            boolean domicile,
            String resultat,
            String score,
            String statut,            // TITULAIRE | REMPLACANT | RESERVE | REPOS | SUSPENDU
            boolean entreEnJeu,
            Integer tempsJeu,         // minutes retenues (peut être null si aucune source)
            String sourceTempsJeu,    // SAISIE | FEDERATION | GPS | null
            short buts,
            short passesDecisives,
            short cartonsJaunes,
            boolean cartonRouge,
            boolean cleanSheet,
            /** Poste occupé ce jour-là, déduit du placement en compo (null si non placé). */
            String posteOccupe) {}

    /** Répartition des présences au groupe. */
    public record Participation(
            int matchsEquipe,
            int convocations,
            int titularisations,
            int entreesEnJeu,
            int remplacantNonEntre,
            int reserve,
            int repos,
            int suspendu,
            /** Convoqué nulle part alors qu'il était apte ce jour-là. */
            int disponibleNonRetenu) {}

    /** Temps de jeu agrégé, avec le détail de ce qui l'alimente. */
    public record TempsJeu(
            int totalMinutes,
            int moyenneParMatchJoue,
            /** Minutes théoriquement disponibles = matchs de l'équipe × durée de référence. */
            int minutesPossibles,
            int partDuTempsPossiblePct,
            int matchsAvecTempsSaisi,
            int matchsAvecTempsFederation,
            int matchsAvecTempsGps) {}

    /** Postes réellement occupés en compo, du plus fréquent au moins fréquent. */
    public record PosteOccupe(String poste, int matchs) {}

    /**
     * Le croisement que rien d'autre ne produit : ce que le joueur encaisse à l'entraînement
     * face à ce qu'il obtient en match. Un joueur à forte charge et faible temps de jeu décroche ;
     * l'inverse signale une compétition non compensée.
     */
    public record ChargeVsJeu(
            double chargeEntrainementUa,
            int minutesJouees,
            int seancesPresentes,
            /** Minutes de match par 1000 UA de charge d'entraînement — null si aucune charge. */
            Double minutesPour1000Ua,
            String lecture) {}

    /** Ce que l'onglet Compétition affiche en entier. */
    public record StatsJoueur(
            UUID joueurId,
            String nom,
            String prenom,
            LocalDate debutPeriode,
            LocalDate finPeriode,
            Participation participation,
            TempsJeu tempsJeu,
            short buts,
            short passesDecisives,
            short cartonsJaunes,
            short cartonsRouges,
            short cleanSheets,
            List<PosteOccupe> postesOccupes,
            ChargeVsJeu chargeVsJeu,
            /** Historique détaillé, du plus récent au plus ancien (sert aussi de sparkline). */
            List<MatchJoueur> matchs) {}
}
