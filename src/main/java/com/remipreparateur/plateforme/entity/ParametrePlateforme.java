package com.remipreparateur.plateforme.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Réglage plateforme, global et réservé au SUPER_ADMIN (rétention des notifications, et tout
 * futur paramètre d'exploitation). Volontairement distinct de {@code configuration} (paramètres
 * métier de performance édités par les clubs) : ici c'est de l'exploitation plateforme, jamais
 * exposée aux clubs. Une ligne par clé.
 */
@Entity
@Table(name = "parametre_plateforme")
@Getter
@Setter
public class ParametrePlateforme {

    @Id
    @Column(name = "cle")
    private String cle;

    @Column(name = "valeur", nullable = false)
    private BigDecimal valeur;

    @Column(name = "libelle", nullable = false)
    private String libelle;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
