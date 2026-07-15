package com.remipreparateur.contrat.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Fiche de paye d'une personne (fiche joueur ou staff) pour une période (1er jour du mois).
 * Transmission PURE : dépôt par le gestionnaire → bouton « Distribuer » (notifie_le) →
 * premier téléchargement par la personne (premier_telechargement_le). Une ligne par
 * (club, personne, mois) — redépôt = remplacement du fichier.
 */
@Entity
@Table(name = "bulletin_paie")
@Getter
@Setter
public class BulletinPaie {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(name = "joueur_id", nullable = false)
    private UUID joueurId;

    /** Période = 1er jour du mois concerné. */
    @Column(name = "periode", nullable = false)
    private LocalDate periode;

    @Column(name = "nom_original", nullable = false)
    private String nomOriginal;

    @Column(name = "type_mime", nullable = false)
    private String typeMime;

    @Column(name = "taille_octets", nullable = false)
    private long tailleOctets;

    @Column(name = "chemin_stockage", nullable = false)
    private String cheminStockage;

    @Column(name = "depose_par")
    private UUID deposePar;

    @Column(name = "depose_le", insertable = false, updatable = false)
    private LocalDateTime deposeLe;

    /** Posé par « Distribuer » : la personne est notifiée et peut télécharger. */
    @Column(name = "notifie_le")
    private LocalDateTime notifieLe;

    @Column(name = "premier_telechargement_le")
    private LocalDateTime premierTelechargementLe;
}
