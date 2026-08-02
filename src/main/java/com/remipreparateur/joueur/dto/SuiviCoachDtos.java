package com.remipreparateur.joueur.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Relation entraîneur ↔ joueur (V98) : objectifs individuels, notes du staff et fil de vie.
 *
 * <p>Le fil de vie n'a pas de table : il agrège à la lecture ce qui est déjà écrit ailleurs
 * (blessures, matchs, entretiens, objectifs, notes). Une table de plus aurait signifié un
 * historique à maintenir en double, donc à désynchroniser.
 */
public final class SuiviCoachDtos {

    private SuiviCoachDtos() {}

    // ── Objectifs ──
    public record ObjectifRequest(
            @NotBlank String titre,
            String description,
            LocalDate echeance,
            String statut) {}

    public record ObjectifResponse(
            UUID id,
            String titre,
            String description,
            LocalDate echeance,
            String statut,
            /** Échéance dépassée alors que l'objectif est toujours en cours. */
            boolean enRetard,
            String auteur,
            LocalDate creeLe) {}

    // ── Notes du staff ──
    public record NoteRequest(
            @NotBlank String texte,
            LocalDate dateNote) {}

    public record NoteResponse(
            UUID id,
            String texte,
            LocalDate dateNote,
            String auteur) {}

    // ── Fil de vie ──

    /**
     * Un évènement de la vie du joueur au club.
     *
     * @param type BLESSURE | RETOUR | MATCH | ENTRETIEN | OBJECTIF | NOTE
     */
    public record EvenementVie(
            LocalDate date,
            String type,
            String titre,
            String detail,
            /** Tonalité d'affichage : ok | warn | bad | neutre. */
            String ton) {}

    public record FilDeVie(
            UUID joueurId,
            LocalDate debut,
            LocalDate fin,
            List<EvenementVie> evenements) {}
}
