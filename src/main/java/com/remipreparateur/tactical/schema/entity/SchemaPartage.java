package com.remipreparateur.tactical.schema.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Diffusion d'un {@link SchemaTactique} aux joueurs (V100).
 *
 * <p>Une ligne = un partage vers UNE cible : {@code equipeId} (toute l'équipe) <b>ou</b>
 * {@code joueurId} (un joueur nommé). Partager le même schéma à l'équipe puis à un joueur en
 * particulier crée deux lignes — l'historique reste ainsi lisible côté staff.
 *
 * <p>Le contenu n'est <b>pas recopié</b> : c'est une référence. Corriger le schéma corrige donc
 * ce que voient les joueurs, à l'inverse d'une diapo qui est un instantané figé. Un schéma
 * partagé est une consigne vivante, pas une photo.
 */
@Entity
@Table(name = "schema_partage")
@Getter
@Setter
public class SchemaPartage {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(name = "schema_id", nullable = false)
    private UUID schemaId;

    /** Équipe destinataire (null si le partage vise un joueur nommé). */
    @Column(name = "equipe_id")
    private UUID equipeId;

    /** Joueur destinataire (null si le partage vise toute l'équipe). */
    @Column(name = "joueur_id")
    private UUID joueurId;

    /** Titre affiché au joueur ; à défaut, le nom du schéma. */
    @Column(name = "titre")
    private String titre;

    /** Consigne du staff qui accompagne le schéma. */
    @Column(name = "message", columnDefinition = "text")
    private String message;

    @Column(name = "cree_par")
    private UUID creePar;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
