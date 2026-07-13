package com.remipreparateur.performance.importation.dto;

import lombok.Data;

@Data
public class ResolutionImportDto {
    /** Identité telle que lue dans le fichier (clé de rattachement aux lignes). */
    private String identiteFichier;
    private String action; // CREATE, MERGE, IGNORE
    private String joueurExistantId;
    private String prenom;
    private String nom;
    private String poste;
}
