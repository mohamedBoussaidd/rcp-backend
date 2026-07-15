package com.remipreparateur.contrat.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Contrat d'une personne du club (fiche joueur OU staff). Niveau CLUB (pas d'equipe_id :
 * la vue équipe se filtre via l'effectif saison). Statut actif/expiré DÉRIVÉ des dates,
 * jamais stocké. Pas de salaire (décision user 2026-07-15) : le PDF signé joint suffit.
 * Fichier physique hors web-root (app.contrats.upload-dir), métadonnées en base.
 */
@Entity
@Table(name = "contrat")
@Getter
@Setter
public class Contrat {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    /** Fiche personne (joueur ou staff). */
    @Column(name = "joueur_id", nullable = false)
    private UUID joueurId;

    /** Type libre côté club : pro, fédéral, CDD, bénévole… */
    @Column(name = "type_contrat", nullable = false)
    private String typeContrat;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    /** null = sans échéance. */
    @Column(name = "date_fin")
    private LocalDate dateFin;

    // ── PDF signé joint (optionnel) ──
    @Column(name = "nom_original")
    private String nomOriginal;

    @Column(name = "type_mime")
    private String typeMime;

    @Column(name = "taille_octets")
    private Long tailleOctets;

    @Column(name = "chemin_stockage")
    private String cheminStockage;

    @Column(name = "notes")
    private String notes;

    @Column(name = "cree_par")
    private UUID creePar;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
