package com.remipreparateur.tactical.regles.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Jeu de règles du moteur tactique, niveau ÉQUIPE. Pour chaque (phase, zone de ballon),
 * une « posture » = position relative des 11 postes du système — contenu porté par
 * {@code reglesJson}, OPAQUE côté Java (le front en possède la sémantique, comme schemaJson).
 *
 * <p>type NOUS = identité de l'équipe (un seul par équipe+système) ; type ADVERSAIRE =
 * profils nommés réutilisables, attachables à un match ({@code match_prepa.profil_adverse_id}).
 */
@Entity
@Table(name = "regle_tactique")
@Getter
@Setter
public class RegleTactique {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "equipe_id", nullable = false)
    private UUID equipeId;

    @Column(name = "type", nullable = false)
    private String type;   // NOUS | ADVERSAIRE

    @Column(name = "nom", nullable = false)
    private String nom;

    @Column(name = "systeme", nullable = false)
    private String systeme;

    @Column(name = "regles_json", columnDefinition = "text", nullable = false)
    private String reglesJson;

    @Column(name = "cree_par")
    private UUID creePar;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
