package com.remipreparateur.performance.seance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "presence")
@Getter
@Setter
public class Presence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "seance_id", nullable = false)
    private UUID seanceId;

    @Column(name = "joueur_id", nullable = false)
    private UUID joueurId;

    @Column(name = "statut", nullable = false)
    @Enumerated(EnumType.STRING)
    private StatutPresence statut = StatutPresence.PRESENT;

    @Column(name = "note")
    private String note;

    /** Origine de la saisie : STAFF (appel) ou JOUEUR (auto-déclaration PWA). */
    @Column(name = "source", nullable = false)
    private String source = "STAFF";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum StatutPresence {
        PRESENT, ABSENT, EXCUSE, RETARD
    }
}
