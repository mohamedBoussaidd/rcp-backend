package com.remipreparateur.performance.importation.dto;

import lombok.Data;
import java.util.List;

/** Corps de POST /api/import-rpe/confirmer : lignes validées + résolutions des joueurs inconnus. */
@Data
public class ConfirmerImportRpeRequest {
    private String seanceId;
    private List<ResolutionImportDto> resolutions;
    private List<LigneRpeImportDto> lignes;
}
