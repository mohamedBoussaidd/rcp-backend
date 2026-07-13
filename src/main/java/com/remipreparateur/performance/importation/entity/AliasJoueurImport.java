package com.remipreparateur.performance.importation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

/**
 * Alias d'identité joueur mémorisé lors d'une résolution d'import (MERGE/CREATE) : la graphie
 * du fichier (« k. benali », normalisée) pointe vers la fiche, pour ne résoudre chaque nom
 * qu'une seule fois par club.
 */
@Entity
@Table(name = "alias_joueur_import")
@Getter
@Setter
public class AliasJoueurImport {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    /** Identité telle que lue dans le fichier, normalisée (ImportNormalisation.normalise). */
    @Column(name = "alias", nullable = false)
    private String alias;

    @Column(name = "joueur_id", nullable = false)
    private UUID joueurId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
