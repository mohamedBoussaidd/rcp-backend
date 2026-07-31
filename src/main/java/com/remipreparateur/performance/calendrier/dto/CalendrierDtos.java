package com.remipreparateur.performance.calendrier.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Couche « contexte » du calendrier : ce qui se lit en survol des jours et des séances
 * sans ouvrir un autre écran. Volontairement COMPACT — c'est la raison d'être de cet
 * endpoint : les lectures existantes ({@code /api/wellness}, {@code /api/rpe}) renvoient
 * tout l'historique sans filtre de dates et ne peuvent pas servir un calendrier.
 */
public final class CalendrierDtos {

    private CalendrierDtos() {}

    /**
     * Un jour de la période affichée.
     *
     * @param wellnessAttendu le ressenti est-il attendu ce jour-là (cadence de l'équipe,
     *        {@code notif_config_equipe.rappel_wellness_jours}, V74) — pas une invention d'écran
     * @param saisis nombre de ressentis effectivement remplis ce jour-là dans la portée
     * @param effectif effectif de référence (0 si inconnu) — le taux se calcule côté front
     * @param moiFait vrai si le joueur connecté a rempli ce jour-là (toujours faux côté staff)
     */
    public record JourRessenti(
            LocalDate date,
            boolean wellnessAttendu,
            int saisis,
            int effectif,
            boolean moiFait) {}

    /**
     * Les retours sRPE agrégés d'UNE séance, pour la pastille de sa carte.
     *
     * @param rpeMoyen intensité moyenne PONDÉRÉE par la durée (une intensité ne s'additionne pas)
     * @param nbPartiels joueurs ayant déclaré moins que la durée planifiée
     */
    public record SeanceRessenti(
            UUID seanceId,
            int nbReponses,
            Double rpeMoyen,
            Integer chargeMoyenne,
            int nbGenes,
            int nbPartiels,
            boolean moiFait) {}

    /**
     * Un anniversaire de la période. Ni la date de naissance ni l'âge ne sortent du serveur :
     * seuls le jour et le mois circulent, ce qui suffit à l'affichage et n'expose rien.
     */
    public record Anniversaire(
            UUID personneId,
            String nom,
            String prenom,
            int jour,
            int mois,
            /** Fiche purement staff (dérivée de l'effectif, comme partout ailleurs). */
            boolean staff) {}

    public record ContexteCalendrier(
            List<JourRessenti> jours,
            List<SeanceRessenti> seances,
            List<Anniversaire> anniversaires) {}
}
