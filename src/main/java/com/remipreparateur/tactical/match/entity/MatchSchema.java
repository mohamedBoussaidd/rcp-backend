package com.remipreparateur.tactical.match.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Schéma adverse attaché à un {@link MatchPrepa}, par COPIE du {@code schemaJson}
 * (snapshot, aucune synchro avec la bibliothèque une fois copié).
 */
@Entity
@Table(name = "match_schema")
@Getter
@Setter
public class MatchSchema {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "titre")
    private String titre;

    @Column(name = "schema_json", nullable = false, columnDefinition = "text")
    private String schemaJson;

    @Column(name = "apercu", columnDefinition = "text")
    private String apercu;

    @Column(name = "ordre", nullable = false)
    private int ordre;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
