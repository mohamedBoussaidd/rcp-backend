package com.remipreparateur.tactical.match.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * État disciplinaire des joueurs à l'approche d'un match (module {@code stats_competition}).
 *
 * <p>Rien de ce qui est renvoyé ici n'est une décision : l'application COMPTE et ALERTE, la
 * commission suspend. Le staff garde la main — le badge se voit au moment de composer, un bouton
 * déclare la suspension en un clic, et aligner quand même reste possible.
 */
public final class SanctionDtos {

    private SanctionDtos() {}

    /**
     * Le cumul d'un joueur et ce qu'il faut en conclure.
     *
     * @param avertissements     matchs avec un avertissement depuis la dernière remise à zéro
     * @param seuil              nombre d'avertissements déclenchant la suspension (configurable)
     * @param seuilAtteint       le cumul a atteint le seuil : suspension à purger
     * @param sousLaMenace       il en manque un seul — c'est l'alerte utile, celle qui anticipe
     * @param expulse            carton rouge non purgé : un match ferme au minimum
     * @param dateExpulsion      date du rouge, pour que le staff retrouve la rencontre
     * @param dejaDeclareSuspendu le joueur est déjà coché suspendu sur ce match : ne rien proposer
     * @param libelle            phrase prête à afficher, pour que l'écran n'ait rien à reformuler
     */
    public record EtatSanction(
            UUID joueurId,
            String nom,
            String prenom,
            int avertissements,
            int seuil,
            boolean seuilAtteint,
            boolean sousLaMenace,
            boolean expulse,
            LocalDate dateExpulsion,
            boolean dejaDeclareSuspendu,
            String libelle) {}

    /**
     * Le tableau disciplinaire d'un match.
     *
     * @param typeMatch      le type sur lequel porte le décompte (un amical ne compte jamais)
     * @param comptabilise   false pour un amical : l'écran doit dire pourquoi il n'affiche rien
     * @param depuis         début de la fenêtre de comptage (saison, ou dernière suspension purgée)
     * @param joueurs        uniquement ceux qui ont quelque chose à signaler
     */
    public record SanctionsMatch(
            UUID matchId,
            String typeMatch,
            boolean comptabilise,
            int seuil,
            LocalDate depuis,
            List<EtatSanction> joueurs) {}
}
