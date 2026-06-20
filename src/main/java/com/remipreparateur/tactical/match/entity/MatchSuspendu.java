package com.remipreparateur.tactical.match.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Joueur suspendu pour un {@link MatchPrepa} (indisponibilité saisie à la main par le staff).
 * Exclu de l'auto-compo au même titre que les blessés (issus du module médical).
 */
@Entity
@Table(name = "match_suspendu")
@Getter
@Setter
public class MatchSuspendu {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "joueur_id", nullable = false)
    private UUID joueurId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
