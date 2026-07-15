package com.remipreparateur.medical.protocole.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Modèle de protocole de reprise (RTP) du club : étapes types clonées sur la blessure à
 * l'initialisation (aucun lien persistant). Les critères CSV (codes du formulaire blessure,
 * null = tous) servent à suggérer automatiquement le bon modèle — même pattern que
 * {@code TypeDocumentRequis.categoriesAge}.
 */
@Entity
@Table(name = "protocole_modele")
@Getter
@Setter
public class ProtocoleModele {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(name = "nom", nullable = false)
    private String nom;

    @Column(name = "description")
    private String description;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    @Column(name = "ordre", nullable = false)
    private short ordre;

    /** CSV de codes type de blessure ; null = tous. */
    @Column(name = "types_blessure")
    private String typesBlessure;

    /** CSV de codes zone corporelle ; null = toutes. */
    @Column(name = "zones_corporelles")
    private String zonesCorporelles;

    /** CSV de codes gravité ; null = toutes. */
    @Column(name = "gravites")
    private String gravites;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
