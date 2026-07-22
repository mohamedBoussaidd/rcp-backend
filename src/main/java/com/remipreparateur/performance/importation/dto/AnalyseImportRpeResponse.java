package com.remipreparateur.performance.importation.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * Réponse de POST /api/import-rpe/analyser.
 * statut PRET : lignes converties + avertissements + joueurs inconnus → aperçu puis résolution.
 * Le format des exports RPE post-séance est fixe (colonnes détectées par en-tête) : pas d'étape
 * de mapping comme l'import GPS.
 */
@Data
public class AnalyseImportRpeResponse {
    private String statut; // PRET
    private String seanceId;
    /** Durée de la séance cible (minutes), utilisée pour la charge sRPE. */
    private Short dureeSeance;
    private List<LigneRpeImportDto> lignes = new ArrayList<>();
    private List<AvertissementImportDto> avertissements = new ArrayList<>();
    private List<JoueurInconnuDto> joueursInconnus = new ArrayList<>();
    private int nbRepondants;
    private int nbSansReponse;
}
