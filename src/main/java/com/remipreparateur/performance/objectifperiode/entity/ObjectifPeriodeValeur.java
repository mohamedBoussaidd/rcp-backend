package com.remipreparateur.performance.objectifperiode.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Une case de l'objectif d'une période, en valeurs ABSOLUES cette fois.
 *
 * <p>Une seule table pour les deux formes d'objectif, distinguées par la colonne renseignée :
 * <ul>
 *   <li>{@code noSemaine} rempli, {@code poste} vide → <b>trajectoire de préparation</b>, une
 *       ligne par semaine, au niveau de l'équipe ;</li>
 *   <li>{@code poste} rempli, {@code noSemaine} vide → <b>cibles de compétition</b>, une
 *       fourchette par poste, valable toute la période.</li>
 * </ul>
 * Deux tables auraient dupliqué la même mécanique de lecture pour une distinction qui tient dans
 * une colonne nulle.
 *
 * <p>{@code modifieManuellement} est ce qui permet à l'éditeur de teinter les cases retouchées et
 * d'avertir avant une régénération : sans ce drapeau, régénérer effacerait silencieusement le
 * travail du préparateur.
 */
@Entity
@Table(name = "objectif_periode_valeur")
@Getter
@Setter
public class ObjectifPeriodeValeur {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "objectif_periode_id", nullable = false)
    private UUID objectifPeriodeId;

    /** Rang de la semaine dans la période (1 = première). Vide en compétition. */
    @Column(name = "no_semaine")
    private Short noSemaine;

    /** Lundi de cette semaine — calculé à la génération, pour ancrer l'affichage sur des dates. */
    @Column(name = "date_lundi")
    private LocalDate dateLundi;

    /** Code de poste de référence. Vide en préparation (l'objectif est d'équipe). */
    @Column(name = "poste")
    private String poste;

    /** Code de {@link com.remipreparateur.performance.referentiel.MetriqueCharge}. */
    @Column(name = "metrique", nullable = false)
    private String metrique;

    @Column(name = "valeur_min")
    private Integer valeurMin;

    @Column(name = "valeur_max")
    private Integer valeurMax;

    /** Priorité héritée de la phase : ce qu'on sacrifie en premier si l'ACWR l'impose. */
    @Column(name = "priorite", nullable = false)
    private String priorite = "IMPORTANT";

    /** Nom de la phase d'origine — alimente le bandeau de l'éditeur. */
    @Column(name = "phase_nom")
    private String phaseNom;

    /** Vrai si le préparateur a retouché la case après génération. */
    @Column(name = "modifie_manuellement", nullable = false)
    private boolean modifieManuellement = false;
}
