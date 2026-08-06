package com.remipreparateur.performance.objectifperiode.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Une PHASE d'un modèle : accumulation, développement, pic, décharge…
 *
 * <p>C'est la phase, et non la semaine, qui est l'unité de stockage — et c'est ce qui sauve le
 * modèle quand la durée change. Une préparation stockée comme six semaines fixes puis interpolée
 * sur neuf semaines perd DEUX choses à la fois : le pic est raboté (il se retrouve moyenné avec
 * ses voisines, on n'atteint plus jamais la valeur maximale) et la décharge se dilue en une
 * descente molle sur deux semaines — c'est-à-dire qu'elle n'est plus une décharge. Le joueur
 * arrive au premier match sans avoir été déchargé, et rien ne l'a signalé.
 *
 * <p>Une phase a son propre bloc de semaines, ou elle n'existe pas. C'est tout l'invariant :
 * <b>on interpole À L'INTÉRIEUR d'une phase, jamais entre deux phases.</b>
 *
 * <p>{@code poidsDuree} est une part relative, pas un nombre de semaines : des poids 2/2/1/1
 * donnent 2/2/1/1 sur six semaines (soit exactement le document de référence), 3/3/2/1 sur neuf,
 * et 1/1/1 sur trois — auquel cas l'application ANNONCE quelle phase elle supprime au lieu de
 * bricoler en silence.
 *
 * <p>Une phase de compétition existe aussi : un modèle de championnat, c'est une phase unique à
 * 100 %. Un seul concept pour les deux cas, donc aucune branche dans le générateur.
 */
@Entity
@Table(name = "modele_objectif_phase")
@Getter
@Setter
public class ModeleObjectifPhase {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "modele_id", nullable = false)
    private UUID modeleId;

    /** Rang dans la période : 0 = première phase. */
    @Column(name = "ordre", nullable = false)
    private Short ordre = 0;

    /** Libellé métier — c'est ce que le préparateur lit dans le bandeau de l'éditeur. */
    @Column(name = "nom", nullable = false)
    private String nom;

    /** Part relative de la durée de la période. Jamais un nombre de semaines. */
    @Column(name = "poids_duree", nullable = false)
    private Short poidsDuree = 1;

    public static final String PREPARATION  = "PREPARATION";
    public static final String COMPETITION  = "COMPETITION";
    public static final String REPRISE      = "REPRISE";
}
