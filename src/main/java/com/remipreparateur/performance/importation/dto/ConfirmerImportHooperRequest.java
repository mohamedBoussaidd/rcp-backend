package com.remipreparateur.performance.importation.dto;

import lombok.Data;
import java.util.List;

/** Corps de POST /api/import-hooper/confirmer : équipe cible + lignes validées + résolutions. */
@Data
public class ConfirmerImportHooperRequest {
    private String equipeId;
    private List<ResolutionImportDto> resolutions;
    private List<LigneHooperImportDto> lignes;
}
