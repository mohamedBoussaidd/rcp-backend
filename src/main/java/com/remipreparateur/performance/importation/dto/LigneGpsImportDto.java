package com.remipreparateur.performance.importation.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class LigneGpsImportDto {
    /** Numéro de la ligne dans le fichier d'origine (1-indexé), pour rattacher les avertissements. */
    private Integer numeroLigne;
    /** Identité telle que lue dans le fichier (prénom seul ou nom complet selon le format). */
    private String identiteFichier;
    private String joueurId;
    /** Nom d'affichage de la fiche appariée (aperçu front) ; null si joueur inconnu. */
    private String joueurNomAffiche;
    private Short dureeMinutes;
    private BigDecimal distanceTotaleM;
    private BigDecimal distance15kmhM;
    private BigDecimal distance19kmhM;
    private BigDecimal distanceSprint24kmhM;
    private BigDecimal distanceSprint28kmhM;
    private Short nbSprints24kmh;
    private BigDecimal vitesseMaxKmh;
    private Short nbAccelerations;
    private Short nbFreinages;
    private BigDecimal ratioDistanceMin;
}
