package com.remipreparateur.performance.seance.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** DTOs des modèles de semaine (gabarits hebdomadaires) et de leur instanciation. */
public class ModeleSemaineDtos {

    /** Créneau-jour d'un modèle (entrée et sortie). */
    public record CreneauDto(
            UUID id,
            short jourSemaine,            // 1 = lundi … 7 = dimanche
            LocalTime heureDebut,
            Short dureeMinutes,
            String terrain,
            UUID typeSeanceId,
            String typeSeanceLibelle,     // sortie seule (lecture)
            String titre,
            String objectif,
            Integer objectifDistanceM,
            Short objectifIntensite,
            short ordre
    ) {}

    /** Modèle complet avec ses créneaux. */
    public record ModeleDto(
            UUID id,
            UUID equipeId,
            String nom,
            String description,
            List<CreneauDto> creneaux
    ) {}

    /** Corps de création / mise à jour d'un modèle. */
    public record ModeleRequest(
            String nom,
            String description,
            List<CreneauDto> creneaux
    ) {}

    /**
     * Demande d'instanciation : génère les séances du modèle pour chaque semaine
     * comprise entre {@code debut} et {@code fin} (bornes incluses, alignées sur le lundi).
     * {@code remplacer} = écraser les séances déjà posées sur les créneaux ciblés.
     */
    public record InstancierRequest(
            LocalDate debut,
            LocalDate fin,
            boolean remplacer
    ) {}

    /** Résultat d'instanciation. */
    public record InstancierResult(
            int creees,
            int ignorees,        // créneaux sautés car séance déjà présente (remplacer=false)
            int remplacees
    ) {}
}
