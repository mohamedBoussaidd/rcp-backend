package com.remipreparateur.notification.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Routage par équipe : quels rôles staff reçoivent un type de notification donné.
 * {@code roles} = CSV de rôles (ex. "PREPARATEUR,MEDICAL"). Permet de réajuster qui
 * s'occupe de quelle alerte sans toucher au code.
 */
@Entity
@Table(name = "notif_routage")
@Getter
@Setter
public class NotifRoutage {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "equipe_id", nullable = false)
    private UUID equipeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private TypeNotification type;

    @Column(name = "roles", nullable = false, length = 160)
    private String roles = "";

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    /** Rôles destinataires sous forme de liste (depuis le CSV). */
    public List<String> rolesList() {
        if (roles == null || roles.isBlank()) return List.of();
        return Arrays.stream(roles.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
