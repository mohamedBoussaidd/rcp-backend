package com.remipreparateur.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Placement d'un joueur dans la compo d'un {@link MatchPrepa}.
 * {@code x}/{@code y} sont des positions relatives sur le terrain ([0..1], n'a
 * de sens que pour un titulaire). {@code statut} ∈ TITULAIRE / REMPLACANT /
 * RESERVE / REPOS / SUSPENDU.
 */
@Entity
@Table(name = "match_compo")
@Getter
@Setter
public class MatchCompo {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "joueur_id", nullable = false)
    private UUID joueurId;

    @Column(name = "x", nullable = false)
    private BigDecimal x = BigDecimal.ZERO;

    @Column(name = "y", nullable = false)
    private BigDecimal y = BigDecimal.ZERO;

    @Column(name = "statut", nullable = false)
    private String statut = "TITULAIRE";
}
