package com.remipreparateur.performance.importation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Anomalie détectée à l'analyse, JAMAIS bloquante : l'utilisateur reste maître (il peut exclure
 * une ligne ou corriger le mapping). Trois niveaux : FICHIER (ex. date ≠ séance), COLONNE
 * (mapping suspect), LIGNE (donnée aberrante chez un joueur).
 */
@Data
@AllArgsConstructor
public class AvertissementImportDto {
    private String niveau;      // FICHIER | COLONNE | LIGNE
    private Integer numeroLigne; // ligne du fichier concernée (niveau LIGNE)
    private String colonne;      // en-tête concerné (niveau COLONNE, parfois LIGNE)
    private String message;
}
