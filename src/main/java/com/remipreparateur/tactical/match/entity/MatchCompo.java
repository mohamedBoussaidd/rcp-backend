package com.remipreparateur.tactical.match.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Placement d'un joueur dans la compo d'un {@link MatchPrepa}.
 * {@code x}/{@code y} sont des positions relatives sur le terrain ([0..1], n'a
 * de sens que pour un titulaire). {@code statut} ∈ TITULAIRE / REMPLACANT /
 * RESERVE / REPOS / SUSPENDU.
 */
@Entity
@Table(name = "match_compo")
@Getter
@Setter
public class MatchCompo {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "joueur_id", nullable = false)
    private UUID joueurId;

    @Column(name = "x", nullable = false)
    private BigDecimal x = BigDecimal.ZERO;

    @Column(name = "y", nullable = false)
    private BigDecimal y = BigDecimal.ZERO;

    @Column(name = "statut", nullable = false)
    private String statut = "TITULAIRE";

    /** Consigne individuelle affichée à ce joueur dans sa PWA (sinon il voit les consignes d'équipe). */
    @Column(name = "consigne", columnDefinition = "text")
    private String consigne;

    // ── Feuille de match (V97) : renseignée APRÈS la rencontre ──

    /**
     * A-t-il foulé la pelouse ? Vrai d'office pour un titulaire, à cocher pour un remplaçant entré.
     * Sert de garde au temps de jeu GPS : un remplaçant qui s'échauffe capteur au dos accumule des
     * minutes qui ne sont pas du temps de jeu.
     */
    @Column(name = "entre_en_jeu", nullable = false)
    private boolean entreEnJeu = false;

    @Column(name = "minute_entree")
    private Short minuteEntree;

    @Column(name = "minute_sortie")
    private Short minuteSortie;

    /** Minutes jouées d'après la fédération (connecteur à venir) — le temps GPS, lui, est dérivé. */
    @Column(name = "temps_jeu_federation")
    private Short tempsJeuFederation;

    @Column(name = "buts", nullable = false)
    private short buts = 0;

    @Column(name = "passes_decisives", nullable = false)
    private short passesDecisives = 0;

    @Column(name = "cartons_jaunes", nullable = false)
    private short cartonsJaunes = 0;

    @Column(name = "carton_rouge", nullable = false)
    private boolean cartonRouge = false;

    // Le clean sheet ne vit plus ici (V99) : il se déduit à la lecture des buts encaissés du match
    // et de l'entrée en jeu. Le figer en base le rendait faux dès qu'on corrigeait le score après
    // coup — même raison que le temps de jeu GPS, jamais stocké lui non plus.
}
