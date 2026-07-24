package com.remipreparateur.badge.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Surcharge de couleur d'un {@link BadgeTon} par un club (paire {fond, texte}). Ne concerne que
 * les badges SYSTÈME (colorés par ton) ; les tags gardent leur couleur explicite. Clé = (club, ton).
 */
@Entity
@Table(name = "badge_couleur_club")
@IdClass(BadgeCouleurClubId.class)
@Getter
@Setter
public class BadgeCouleurClub {

    @Id
    @Column(name = "club_id")
    private UUID clubId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "ton")
    private BadgeTon ton;

    @Column(name = "couleur_bg", nullable = false)
    private String couleurBg;

    @Column(name = "couleur_fg", nullable = false)
    private String couleurFg;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
