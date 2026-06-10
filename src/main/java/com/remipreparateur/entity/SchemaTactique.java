package com.remipreparateur.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Schéma tactique réutilisable, partagé au sein d'un club (bibliothèque).
 * Édité avec le même éditeur que les schémas d'exercice ; l'attachement à un
 * exercice se fait par copie du {@code schemaJson} (aucune synchro ensuite).
 */
@Entity
@Table(name = "schema_tactique")
@Getter
@Setter
public class SchemaTactique {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(name = "nom", nullable = false)
    private String nom;

    @Column(name = "categorie")
    private String categorie;

    @Column(name = "schema_json", columnDefinition = "text", nullable = false)
    private String schemaJson;

    @Column(name = "apercu", columnDefinition = "text")
    private String apercu;

    @Column(name = "cree_par")
    private UUID creePar;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
