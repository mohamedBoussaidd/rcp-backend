package com.remipreparateur.joueur.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Observation du staff sportif sur un joueur (V98).
 *
 * <p>Réservée au staff : elle n'est jamais remontée au joueur dans la PWA, au même titre que la
 * consigne médicale d'une présence aménagée.
 */
@Entity
@Table(name = "note_joueur")
@Getter
@Setter
public class NoteJoueur {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "joueur_id", nullable = false)
    private UUID joueurId;

    @Column(name = "texte", nullable = false, columnDefinition = "text")
    private String texte;

    @Column(name = "date_note", nullable = false)
    private LocalDate dateNote = LocalDate.now();

    @Column(name = "auteur_id")
    private UUID auteurId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
