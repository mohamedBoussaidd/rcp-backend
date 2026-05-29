package com.remipreparateur.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "historique_poids")
@Getter
@Setter
public class HistoriquePoids {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "joueur_id", nullable = false)
    private Joueur joueur;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "poids", nullable = false, precision = 5, scale = 2)
    private BigDecimal poids;

    @Column(name = "commentaire")
    private String commentaire;
}
