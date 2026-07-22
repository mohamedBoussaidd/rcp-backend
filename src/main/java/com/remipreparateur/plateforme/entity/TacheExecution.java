package com.remipreparateur.plateforme.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Journal d'exécution d'une tâche de maintenance (planifiée ou déclenchée à la main depuis la
 * console super-admin). On conserve l'historique ; l'écran n'affiche que la dernière par code.
 */
@Entity
@Table(name = "tache_execution")
@Getter
@Setter
public class TacheExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "code", nullable = false, length = 60)
    private String code;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    /** SUCCES | ECHEC */
    @Column(name = "statut", nullable = false, length = 20)
    private String statut;

    @Column(name = "message", length = 500)
    private String message;

    /** Compte ayant déclenché manuellement (null si exécution planifiée). */
    @Column(name = "declenche_par")
    private UUID declenchePar;
}
