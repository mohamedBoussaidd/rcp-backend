package com.remipreparateur.performance.importation.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class LigneGpsImportDto {
    private String prenomFichier;
    private String joueurId;
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
