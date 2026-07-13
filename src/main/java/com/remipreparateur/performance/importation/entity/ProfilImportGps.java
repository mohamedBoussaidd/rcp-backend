package com.remipreparateur.performance.importation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

/**
 * Profil de mapping d'import GPS. club_id NULL = profil global « fournisseur » (McLloyd…)
 * proposé à tous les clubs, en lecture seule pour eux. Le profil d'un club est reconnu
 * automatiquement aux imports suivants par la signature de ses en-têtes.
 */
@Entity
@Table(name = "profil_import_gps")
@Getter
@Setter
public class ProfilImportGps {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "club_id")
    private UUID clubId;

    @Column(name = "nom", nullable = false)
    private String nom;

    @Column(name = "signature_entetes", nullable = false)
    private String signatureEntetes;

    @Column(name = "format_identite", nullable = false)
    private String formatIdentite = "PRENOM_NOM"; // PRENOM | PRENOM_NOM | NOM_PRENOM

    /** JSON : liste de {@link com.remipreparateur.performance.importation.dto.MappingColonne}. */
    @Column(name = "mappings", nullable = false)
    private String mappings;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
