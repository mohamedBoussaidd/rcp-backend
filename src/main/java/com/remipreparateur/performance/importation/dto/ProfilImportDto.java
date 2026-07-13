package com.remipreparateur.performance.importation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

/** Profil de mapping exposé au front (les mappings permettent de pré-remplir l'écran). */
@Data
@AllArgsConstructor
public class ProfilImportDto {
    private String id;
    private String nom;
    private boolean global;          // true = profil fournisseur (club_id NULL), lecture seule
    private String formatIdentite;
    private List<MappingColonne> mappings;
}
