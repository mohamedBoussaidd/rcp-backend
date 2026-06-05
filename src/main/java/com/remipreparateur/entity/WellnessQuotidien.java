package com.remipreparateur.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Ressenti quotidien du joueur (indice de Hooper, 5 items sur 1..5).
 * Une saisie par jour (unique joueur_id + date), modifiable dans la journée.
 */
@Entity
@Table(name = "wellness_quotidien")
@Getter
@Setter
public class WellnessQuotidien {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "joueur_id", nullable = false)
    private UUID joueurId;

    @Column(name = "equipe_id")
    private UUID equipeId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "sommeil", nullable = false)
    private Short sommeil;

    @Column(name = "fatigue", nullable = false)
    private Short fatigue;

    @Column(name = "douleur", nullable = false)
    private Short douleur;

    @Column(name = "stress", nullable = false)
    private Short stress;

    @Column(name = "humeur", nullable = false)
    private Short humeur;

    @Column(name = "commentaire")
    private String commentaire;

    /** Signalement de gêne localisée (null = aucune gêne ce jour). */
    @Column(name = "gene_zone")
    private String geneZone;

    @Column(name = "gene_intensite")
    private Short geneIntensite;

    /** Moment de la gêne : EFFORT | APRES | REPOS. */
    @Column(name = "gene_moment")
    private String geneMoment;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
