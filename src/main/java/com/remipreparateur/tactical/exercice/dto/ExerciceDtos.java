package com.remipreparateur.tactical.exercice.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.UUID;

/** DTOs de la bibliotheque d'exercices (niveau club). */
public final class ExerciceDtos {

    private ExerciceDtos() {}

    /**
     * Champs du mode avancé (module seance_avancee) : cadre pédagogique + organisation.
     * Tous optionnels — appliqués uniquement si l'utilisateur a `seance_avancee:access`
     * (sinon ignorés, les valeurs existantes sont préservées).
     */
    public record ExerciceAvance(
            String contextePedagogique,
            String niveauObjectif,      // TEMPS_DE_JEU / PRINCIPE_ACTION / REGLE_ACTION_COLLECTIVE / REGLE_ACTION_INDIVIDUELLE / MOYEN
            String echelleEffectif,     // COLLECTIF / INTERSECTORIEL / SECTORIEL / GROUPAL / INDIVIDUEL
            String dominanteTactiqueOrg,
            String dominanteTactiqueFonc,
            String dominanteMental,
            String dominanteTechnique,
            String dominanteAthletique,
            String butSystemeMarque,
            String reglesJeu,
            String variablesPedagogiques,
            String reperesPerceptifs,
            String comportementsAttendus,
            BigDecimal terrainLongueurM,
            BigDecimal terrainLargeurM,
            String formatJoueurs,
            Short nbJoueursTotal,
            String sequencage) {}

    public record ExerciceRequest(
            @NotBlank String nom,
            String categorie,
            String type,            // PHYSIQUE / TECHNIQUE / MIXTE (defaut TECHNIQUE)
            Short dureeMinutes,
            String objectif,
            Short intensite,        // 1..5
            String description,
            Integer distanceAttendueM,
            Integer distanceHauteIntensiteM,
            Short nbSprints,
            ExerciceAvance avance,
            UUID photoImportId) {}   // import photo d'origine (pièce jointe), nullable

    public record ExerciceResponse(
            UUID id,
            String nom,
            String categorie,
            String type,
            Short dureeMinutes,
            String objectif,
            Short intensite,
            String description,
            String schemaJson,
            Integer distanceAttendueM,
            Integer distanceHauteIntensiteM,
            Short nbSprints,
            UUID creeParId,
            String creeParNom,
            UUID equipeOrigineId,
            String equipeOrigineNom,
            boolean modifiable,
            ExerciceAvance avance,
            UUID photoImportId) {}

    /** Sauvegarde du schéma tactique (éditeur Konva). */
    public record SchemaRequest(String schemaJson) {}
}
