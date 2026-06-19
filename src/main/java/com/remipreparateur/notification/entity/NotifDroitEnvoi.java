package com.remipreparateur.notification.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

/**
 * Droit d'émission accordé à un joueur (par le staff). {@code niveau} = AUCUN / EQUIPE / CIBLE.
 * Absence de ligne = AUCUN.
 */
@Entity
@Table(name = "notif_droit_envoi")
@Getter
@Setter
public class NotifDroitEnvoi {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "joueur_id", nullable = false)
    private UUID joueurId;

    @Column(name = "equipe_id", nullable = false)
    private UUID equipeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "niveau", nullable = false, length = 10)
    private NiveauEnvoi niveau = NiveauEnvoi.AUCUN;
}
