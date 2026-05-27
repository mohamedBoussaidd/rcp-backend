package com.remipreparateur.dto;

import lombok.Data;
import java.util.List;

@Data
public class ConfirmerImportRequest {
    private String seanceId;
    private List<ResolutionImportDto> resolutions;
    private List<LigneGpsImportDto> lignes;
}
