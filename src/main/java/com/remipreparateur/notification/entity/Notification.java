package com.remipreparateur.notification.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Une notification délivrée à UN destinataire. Les producteurs (scheduler, événements,
 * messages) font le fan-out : une ligne par destinataire. {@code sujetJoueurId} = joueur
 * concerné par une alerte (différent du destinataire). {@code threadId}/{@code repondable}
 * préparent l'évolution du chat (réponses sur certains types).
 */
@Entity
@Table(name = "notification")
@Getter
@Setter
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "equipe_id", nullable = false)
    private UUID equipeId;

    @Column(name = "destinataire_user_id", nullable = false)
    private UUID destinataireUserId;

    /** Joueur concerné (contexte d'une alerte), nullable. */
    @Column(name = "sujet_joueur_id")
    private UUID sujetJoueurId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private TypeNotification type;

    @Enumerated(EnumType.STRING)
    @Column(name = "emetteur_type", nullable = false, length = 20)
    private EmetteurType emetteurType = EmetteurType.SYSTEME;

    @Column(name = "emetteur_user_id")
    private UUID emetteurUserId;

    @Column(name = "titre", nullable = false, length = 160)
    private String titre;

    @Column(name = "corps")
    private String corps;

    @Column(name = "lien", length = 255)
    private String lien;

    @Enumerated(EnumType.STRING)
    @Column(name = "priorite", nullable = false, length = 10)
    private Priorite priorite = Priorite.NORMALE;

    @Column(name = "thread_id")
    private UUID threadId;

    @Column(name = "repondable", nullable = false)
    private boolean repondable = false;

    @Column(name = "lu", nullable = false)
    private boolean lu = false;

    @Column(name = "lu_at")
    private LocalDateTime luAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
