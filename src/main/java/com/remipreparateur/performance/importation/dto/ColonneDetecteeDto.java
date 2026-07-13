package com.remipreparateur.performance.importation.dto;

import lombok.Data;
import java.util.List;

/** Colonne détectée dans le fichier, avec aperçu et suggestion de mapping (nullable). */
@Data
public class ColonneDetecteeDto {
    private String entete;           // en-tête brut du fichier
    private String enteteNormalise;  // clé du mapping
    private List<String> apercu;     // 3 premières valeurs non vides
    private MappingColonne suggestion;
}
