package com.remipreparateur.tactical.match.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Joueur à surveiller pour un {@link MatchPrepa} (consigne de prépa).
 * {@code cible} = ADVERSE (nom libre, effectif adverse non en base) ou
 * EQUIPE (un de nos joueurs, {@code joueurId}).
 */
@Entity
@Table(name = "match_joueur_surveille")
@Getter
@Setter
public class MatchJoueurSurveille {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "cible", nullable = false)
    private String cible = "ADVERSE";

    @Column(name = "joueur_id")
    private UUID joueurId;

    @Column(name = "nom")
    private String nom;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
