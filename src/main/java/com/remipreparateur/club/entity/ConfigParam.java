package com.remipreparateur.club.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "configuration")
@Getter
@Setter
public class ConfigParam {

    @Id
    @Column(name = "cle")
    private String cle;

    @Column(name = "valeur", nullable = false)
    private BigDecimal valeur;

    @Column(name = "valeur_defaut", nullable = false)
    private BigDecimal valeurDefaut;

    @Column(name = "groupe", nullable = false)
    private String groupe;

    @Column(name = "niveau", nullable = false)
    private Integer niveau;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
