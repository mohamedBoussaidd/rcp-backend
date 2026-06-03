package com.remipreparateur.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "blessure")
@Getter
@Setter
public class Blessure {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "joueur_id", nullable = false)
    private UUID joueurId;

    @Column(name = "blessure_precedente_id")
    private UUID blessurePrecedenteId;

    @Column(name = "date_blessure", nullable = false)
    private LocalDate dateBlessure;

    @Column(name = "date_retour_effectif")
    private LocalDate dateRetourEffectif;

    @Column(name = "type_blessure")
    private String typeBlessure;

    @Column(name = "zone_corporelle")
    private String zoneCorporelle;

    @Column(name = "cote")
    private String cote;

    @Column(name = "gravite")
    private String gravite;

    @Column(name = "cause_probable")
    private String causeProbable;

    @Column(name = "recidive")
    private boolean recidive = false;

    @Column(name = "commentaire")
    private String commentaire;

    @Column(name = "equipe_id")
    private UUID equipeId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
