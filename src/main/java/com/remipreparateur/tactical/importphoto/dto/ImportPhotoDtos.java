package com.remipreparateur.tactical.importphoto.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** DTOs de l'import photo (IA vision) : contenu extrait + schéma prêt pour l'éditeur. */
public final class ImportPhotoDtos {

    private ImportPhotoDtos() {}

    public record BlocExtrait(String libelle, Integer dureeMinutes, String sequencage, String consignes) {}

    /** Champs avancés détectés (alignés sur ExerciceAvance du chantier séance_avancée). */
    public record AvanceExtrait(
            String formatJoueurs,
            BigDecimal terrainLongueurM,
            BigDecimal terrainLargeurM,
            String sequencage,
            String butSystemeMarque,
            String reglesJeu,
            String variablesPedagogiques) {}

    public record TexteExtrait(
            String type,             // SEANCE | EXERCICE
            String titre,
            String description,
            String objectif,
            Integer dureeMinutes,
            String materiel,
            List<BlocExtrait> blocs,
            List<String> dominantes,       // codes du référentiel V61 (validés côté serveur)
            List<String> sousPrincipes,    // idem
            AvanceExtrait avance) {}

    /**
     * Résultat complet : contenu texte + schéma converti au format de l'éditeur Konva
     * (coordonnées pixels, couleurs réelles, ids générés) — chargeable tel quel.
     */
    public record ImportPhotoResponse(
            UUID journalId,
            TexteExtrait texte,
            String schemaJson,
            int nbElements,
            int nbTraces) {}
}
