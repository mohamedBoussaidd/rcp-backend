package com.remipreparateur.performance.objectifperiode.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Le niveau de charge d'une phase, POUR UNE MÉTRIQUE, en pourcentage de la cible du référentiel.
 *
 * <p>Un pourcentage PAR MÉTRIQUE, jamais un coefficient global appliqué à toute la semaine :
 * les courbes ne se ressemblent pas. Sur le document de référence, le volume monte de 67 % à
 * 109 % de la charge de championnat pendant que la haute intensité part de 45 % et culmine à
 * 116 %. On ne réintroduit pas la vitesse au rythme du volume — un coefficient unique écraserait
 * précisément la distinction qui fait le métier.
 *
 * <p>{@code pctDebut} et {@code pctFin} bornent la phase ; les semaines à l'intérieur interpolent
 * entre les deux. Une phase plate (pic, décharge) porte deux fois la même valeur.
 *
 * <p><b>Exception des métriques d'exposition</b> : pour {@code expo_vmax}, le pourcentage n'est
 * pas un pourcentage du référentiel mais la cible elle-même (% du record personnel). « 109 % de
 * 90 % » n'a aucun sens ; « atteindre 94 % de son record cette semaine » en a un.
 *
 * @see com.remipreparateur.performance.referentiel.PrioriteMetrique pour ce qu'on sacrifie d'abord
 */
@Entity
@Table(name = "modele_objectif_phase_valeur",
       uniqueConstraints = @UniqueConstraint(columnNames = {"phase_id", "metrique"}))
@Getter
@Setter
public class ModeleObjectifPhaseValeur {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "phase_id", nullable = false)
    private UUID phaseId;

    /** Code de {@link com.remipreparateur.performance.referentiel.MetriqueCharge}. */
    @Column(name = "metrique", nullable = false)
    private String metrique;

    /** Niveau au DÉBUT de la phase, en % de la cible du référentiel. */
    @Column(name = "pct_debut", nullable = false)
    private Integer pctDebut;

    /** Niveau à la FIN de la phase. Égal à {@code pctDebut} pour une phase plate. */
    @Column(name = "pct_fin", nullable = false)
    private Integer pctFin;

    /** SECONDAIRE | IMPORTANT | INTOUCHABLE — ce qu'on accepte de rogner si l'ACWR l'impose. */
    @Column(name = "priorite", nullable = false)
    private String priorite = "IMPORTANT";
}
