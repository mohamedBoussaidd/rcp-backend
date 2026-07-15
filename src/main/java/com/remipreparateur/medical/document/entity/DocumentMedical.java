package com.remipreparateur.medical.document.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Document medical depose par le joueur pour lui-meme.
 * Le fichier physique vit hors web root (cf. app.medical.upload-dir) ; seules les
 * metadonnees et le chemin de stockage sont en base.
 */
@Entity
@Table(name = "document_medical")
@Getter
@Setter
public class DocumentMedical {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "joueur_id", nullable = false)
    private UUID joueurId;

    @Column(name = "equipe_id")
    private UUID equipeId;

    /** Renseigné pour une déclaration (arrêt / accident de travail) rattachée à une blessure. */
    @Column(name = "blessure_id")
    private UUID blessureId;

    @Column(name = "nom_original", nullable = false)
    private String nomOriginal;

    @Column(name = "type_mime", nullable = false)
    private String typeMime;

    @Column(name = "taille_octets", nullable = false)
    private long tailleOctets;

    @Column(name = "chemin_stockage", nullable = false)
    private String cheminStockage;

    @Column(name = "categorie", nullable = false)
    private String categorie;

    @Column(name = "description")
    private String description;

    /** CSV des roles autorises EN PLUS du medical (ex. "ENTRAINEUR,PRESIDENT"). */
    @Column(name = "partage_roles")
    private String partageRoles;

    @Column(name = "depose_par")
    private UUID deposePar;

    @Column(name = "date_depot", insertable = false, updatable = false)
    private LocalDateTime dateDepot;
}
