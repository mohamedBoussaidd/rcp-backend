package com.remipreparateur.dto;

import lombok.Data;

@Data
public class ResolutionImportDto {
    private String prenomFichier;
    private String action; // CREATE, MERGE, IGNORE
    private String joueurExistantId;
    private String prenom;
    private String nom;
    private String poste;
}
