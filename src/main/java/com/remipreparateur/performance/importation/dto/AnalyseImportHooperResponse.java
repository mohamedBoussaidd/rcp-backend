package com.remipreparateur.performance.importation.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * Réponse de POST /api/import-hooper/analyser.
 * statut PRET : lignes converties + avertissements + joueurs inconnus → aperçu puis résolution.
 * Le format de l'export « playermonitoring » est fixe (colonnes détectées par en-tête) : pas
 * d'étape de mapping comme l'import GPS. Clé métier = équipe (le club de matching en découle) et
 * date lue dans le fichier (pas de séance à sélectionner).
 */
@Data
public class AnalyseImportHooperResponse {
    private String statut; // PRET
    /** Équipe cible choisie par le staff (club de matching + périmètre). */
    private String equipeId;
    private List<LigneHooperImportDto> lignes = new ArrayList<>();
    private List<AvertissementImportDto> avertissements = new ArrayList<>();
    private List<JoueurInconnuDto> joueursInconnus = new ArrayList<>();
    private int nbRepondants;
    private int nbSansReponse;
}
