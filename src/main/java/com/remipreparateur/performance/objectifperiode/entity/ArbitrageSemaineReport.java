package com.remipreparateur.performance.objectifperiode.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Delta produit par un arbitrage sur UNE semaine et UNE métrique, en unité de la métrique
 * (mètres, ou un nombre pour les sprints).
 *
 * <p>C'est ce qui permet de ne jamais toucher à l'objectif de période : le prescrit du modèle
 * reste la référence, et le Retenu affiché vaut « prescrit + somme des deltas qui ciblent cette
 * semaine ». Retirer l'arbitrage rétablit la trajectoire d'origine sans rien régénérer, et
 * l'écran peut nommer l'origine de l'écart (« +2 km reportés de la semaine du 10/03 »).
 *
 * <p>Pour un {@code RELISSER}, la semaine source porte le delta négatif et les semaines qui
 * reçoivent le report portent les positifs : <b>la somme d'un arbitrage est nulle</b>, invariant
 * vérifiable d'un coup d'œil. Les métriques marquées INTOUCHABLE sur la phase ne sont jamais
 * reportées — on sacrifie du volume, jamais l'exposition haute vitesse.
 */
@Entity
@Table(name = "arbitrage_semaine_report",
       uniqueConstraints = @UniqueConstraint(columnNames = {"arbitrage_id", "date_lundi_cible", "metrique"}))
@Getter
@Setter
public class ArbitrageSemaineReport {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "arbitrage_id", nullable = false)
    private UUID arbitrageId;

    /** Semaine qui porte ce delta — pas forcément celle qui a été arbitrée. */
    @Column(name = "date_lundi_cible", nullable = false)
    private LocalDate dateLundiCible;

    @Column(name = "metrique", nullable = false, length = 40)
    private String metrique;

    /** Signé : négatif sur la semaine allégée, positif sur celles qui reçoivent. */
    @Column(name = "delta", nullable = false)
    private Integer delta;
}
