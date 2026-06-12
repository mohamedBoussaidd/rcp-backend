package com.remipreparateur.medical.conseil.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Conseil rédigé par le staff (médical / préparateur) et affiché au joueur dans son
 * suivi subjectif. {@code joueurId} null = conseil commun à l'équipe ; sinon conseil
 * personnel à ce joueur (affiché en plus des conseils d'équipe).
 */
@Entity
@Table(name = "conseil_staff")
@Getter
@Setter
public class ConseilStaff {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "equipe_id", nullable = false)
    private UUID equipeId;

    /** null = conseil d'équipe ; sinon conseil personnel à ce joueur. */
    @Column(name = "joueur_id")
    private UUID joueurId;

    @Column(name = "titre", nullable = false)
    private String titre;

    @Column(name = "texte", nullable = false)
    private String texte;

    /** Clé d'icône interprétée côté front (ex. HYDRATATION, SOMMEIL, MOBILITE). */
    @Column(name = "icone")
    private String icone;

    @Column(name = "cree_par")
    private UUID creePar;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
