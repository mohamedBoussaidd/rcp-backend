package com.remipreparateur.documentadmin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Document déposé pour un joueur, en réponse à un {@link TypeDocumentRequis} du club.
 * Le statut {@code MANQUANT} n'est JAMAIS stocké : il correspond à l'absence de ligne pour un
 * (joueur, type) applicable — cf. {@code DocumentAdminService}. Écrasement simple au redépôt
 * (pas d'historique des refus, cohérent avec {@code DocumentMedical}).
 */
@Entity
@Table(name = "document_joueur")
@Getter
@Setter
public class DocumentJoueur {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(name = "joueur_id", nullable = false)
    private UUID joueurId;

    @Column(name = "type_document_requis_id", nullable = false)
    private UUID typeDocumentRequisId;

    /** 'SOUMIS' | 'VALIDE' | 'REFUSE' | 'EXPIRE'. */
    @Column(name = "statut", nullable = false)
    private String statut;

    @Column(name = "chemin_stockage")
    private String cheminStockage;

    @Column(name = "nom_original")
    private String nomOriginal;

    @Column(name = "type_mime")
    private String typeMime;

    @Column(name = "taille_octets")
    private Long tailleOctets;

    @Column(name = "date_soumission")
    private LocalDateTime dateSoumission;

    @Column(name = "date_validation")
    private LocalDateTime dateValidation;

    @Column(name = "valide_par")
    private UUID validePar;

    @Column(name = "motif_refus")
    private String motifRefus;

    @Column(name = "date_expiration")
    private LocalDate dateExpiration;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
