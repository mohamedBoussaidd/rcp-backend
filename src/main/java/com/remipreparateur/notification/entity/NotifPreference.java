package com.remipreparateur.notification.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

/**
 * Préférence d'un destinataire pour un type de notification.
 * {@code actif} = reçoit ou non ce type ; {@code verrouilleParStaff} = true quand le staff
 * a retiré au destinataire le droit de modifier lui-même cette préférence.
 */
@Entity
@Table(name = "notif_preference")
@Getter
@Setter
public class NotifPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private TypeNotification type;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    @Column(name = "verrouille_par_staff", nullable = false)
    private boolean verrouilleParStaff = false;
}
