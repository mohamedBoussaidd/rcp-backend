package com.remipreparateur.ia.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Journal de consommation IA : une ligne par appel (club × feature × provider × modèle).
 * Sert à la traçabilité par club et au décompte des quotas quotidiens de la clé globale.
 */
@Entity
@Table(name = "ia_usage")
@Getter
@Setter
public class IaUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "club_id")
    private UUID clubId;

    /** import_photo | generateur_seance | … */
    @Column(name = "feature", nullable = false, length = 40)
    private String feature;

    @Column(name = "provider", length = 20)
    private String provider;

    @Column(name = "modele", length = 80)
    private String modele;

    /** true = a utilisé la clé globale (soumis au quota) ; false = clé propre du club. */
    @Column(name = "cle_globale", nullable = false)
    private boolean cleGlobale;

    @Column(name = "jour", nullable = false)
    private LocalDate jour;

    @Column(name = "cree_at", nullable = false)
    private LocalDateTime creeAt = LocalDateTime.now();
}
