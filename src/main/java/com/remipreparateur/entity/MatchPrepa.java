package com.remipreparateur.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Match (cycle de vie avant/après), niveau ÉQUIPE.
 * AVANT : adversaire, consignes, schémas adverses ({@link MatchSchema}) et
 * compo ({@link MatchCompo}). APRÈS : résultat, score, notes de débrief et
 * lien manuel vers une session GPS ({@code sessionGpsId} → id d'une {@code seance}).
 */
@Entity
@Table(name = "match_prepa")
@Getter
@Setter
public class MatchPrepa {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "equipe_id", nullable = false)
    private UUID equipeId;

    @Column(name = "adversaire", nullable = false)
    private String adversaire;

    @Column(name = "date_match")
    private LocalDate dateMatch;

    @Column(name = "competition")
    private String competition;

    @Column(name = "domicile", nullable = false)
    private boolean domicile = true;

    @Column(name = "consignes", columnDefinition = "text")
    private String consignes;

    @Column(name = "resultat")
    private String resultat;

    @Column(name = "score")
    private String score;

    @Column(name = "notes_debrief", columnDefinition = "text")
    private String notesDebrief;

    @Column(name = "session_gps_id")
    private UUID sessionGpsId;

    @Column(name = "cree_par")
    private UUID creePar;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
