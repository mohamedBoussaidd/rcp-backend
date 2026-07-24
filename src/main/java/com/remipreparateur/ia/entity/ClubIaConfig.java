package com.remipreparateur.ia.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Configuration IA d'un club (pilotée par le super-admin) : fournisseur, clé API chiffrée et
 * modèle. Une ligne par club. En l'absence de config, le club retombe sur la clé globale
 * (variable d'environnement) plafonnée par des quotas par feature.
 */
@Entity
@Table(name = "club_ia_config")
@Getter
@Setter
public class ClubIaConfig {

    @Id
    @Column(name = "club_id")
    private UUID clubId;

    /** ANTHROPIC | OPENAI */
    @Column(name = "provider", nullable = false, length = 20)
    private String provider = "ANTHROPIC";

    /** Clé API chiffrée (AES-GCM) — jamais renvoyée en clair par l'API. */
    @Column(name = "cle_api_chiffree", columnDefinition = "text")
    private String cleApiChiffree;

    @Column(name = "modele", nullable = false, length = 80)
    private String modele;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
