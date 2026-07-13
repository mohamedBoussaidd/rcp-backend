package com.remipreparateur.performance.importation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Identité du fichier sans correspondance en base, avec découpage suggéré pour le CREATE. */
@Data
@AllArgsConstructor
public class JoueurInconnuDto {
    private String identiteFichier;
    private String prenomSuggere;
    private String nomSuggere;
}
