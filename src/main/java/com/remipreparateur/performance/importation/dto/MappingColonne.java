package com.remipreparateur.performance.importation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Association d'une colonne de fichier (en-tête NORMALISÉ) à une métrique. Sérialisé en JSON
 * dans profil_import_gps.mappings — tout champ ajouté doit rester rétro-compatible avec les
 * profils déjà en base (seed V56 inclus).
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MappingColonne {
    private String entete;            // en-tête normalisé (ImportNormalisation.normalise)
    private MetriqueImport metrique;
    private Double facteur;           // multiplicateur d'unité (km→m : 1000) ; null = 1
    private Double seuilReel;         // seuil réel du fichier pour les zones (ex. 19.8)
    private String semantique;        // CUMUL (>seuil) | BANDE (plage seuil→zone supérieure)
    private String formatDuree;       // HMS | MINUTES | SECONDES (métrique DUREE uniquement)

    public double facteurOuUn() {
        return facteur == null ? 1.0 : facteur;
    }
}
