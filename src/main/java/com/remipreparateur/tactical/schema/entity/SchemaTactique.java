package com.remipreparateur.tactical.schema.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Schéma tactique réutilisable, partagé au sein d'un club (bibliothèque).
 * Édité avec le même éditeur que les schémas d'exercice ; l'attachement à un
 * exercice se fait par copie du {@code schemaJson} (aucune synchro ensuite).
 *
 * <p><b>{@code clubId} null = schéma FOURNI</b> (V64) : posé par le super-admin, visible par tous
 * les clubs pour qu'une bibliothèque neuve ne soit pas vide. Un club ne l'édite jamais sur place,
 * il le <b>copie</b> chez lui ({@code dupliquer}). Même patron que {@code ProfilImportGps}.
 */
@Entity
@Table(name = "schema_tactique")
@Getter
@Setter
public class SchemaTactique {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    /** null = schéma fourni (global, super-admin). */
    @Column(name = "club_id")
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
