package com.remipreparateur.tactical.exercice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "exercice")
@Getter
@Setter
public class Exercice {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(name = "nom", nullable = false)
    private String nom;

    @Column(name = "categorie")
    private String categorie;

    // PHYSIQUE / TECHNIQUE / MIXTE : oriente le contenu et l'usage des attentes physiques.
    @Column(name = "type", nullable = false, length = 20)
    private String type = "TECHNIQUE";

    @Column(name = "duree_minutes")
    private Short dureeMinutes;

    @Column(name = "objectif")
    private String objectif;

    @Column(name = "intensite")
    private Short intensite;

    // ── Attentes physiques (optionnelles, surtout pour les exercices PHYSIQUE) ──
    @Column(name = "distance_attendue_m")
    private Integer distanceAttendueM;

    @Column(name = "distance_haute_intensite_m")
    private Integer distanceHauteIntensiteM;

    @Column(name = "nb_sprints")
    private Short nbSprints;

    @Column(name = "description")
    private String description;

    @Column(name = "schema_json", columnDefinition = "text")
    private String schemaJson;

    @Column(name = "cree_par")
    private UUID creePar;

    @Column(name = "equipe_origine_id")
    private UUID equipeOrigineId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
