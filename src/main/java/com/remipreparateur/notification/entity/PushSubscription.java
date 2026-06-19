package com.remipreparateur.notification.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

/** Abonnement Web Push d'un device (navigateur) d'un utilisateur. */
@Entity
@Table(name = "push_subscription")
@Getter
@Setter
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "endpoint", nullable = false)
    private String endpoint;

    @Column(name = "p256dh", nullable = false, length = 255)
    private String p256dh;

    @Column(name = "auth", nullable = false, length = 255)
    private String auth;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
