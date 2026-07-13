package com.remipreparateur.performance.importation.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * Réponse de POST /api/import/analyser.
 * statut MAPPING_REQUIS : format inconnu → le front affiche l'écran de mapping (colonnes +
 * suggestions + profils disponibles) puis rappelle l'endpoint avec les mappings validés.
 * statut PRET : lignes converties + avertissements + joueurs inconnus → aperçu puis résolution.
 */
@Data
public class AnalyseImportResponse {
    private String statut; // MAPPING_REQUIS | PRET
    private String seanceId;
    private String signatureEntetes;
    private String formatIdentiteSuggere;
    private List<ColonneDetecteeDto> colonnes = new ArrayList<>();
    private List<ProfilImportDto> profilsDisponibles = new ArrayList<>();
    private ProfilImportDto profilUtilise;
    private List<LigneGpsImportDto> lignes = new ArrayList<>();
    private List<AvertissementImportDto> avertissements = new ArrayList<>();
    private List<JoueurInconnuDto> joueursInconnus = new ArrayList<>();
}
