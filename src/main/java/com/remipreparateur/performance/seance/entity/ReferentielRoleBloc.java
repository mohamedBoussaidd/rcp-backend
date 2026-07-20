package com.remipreparateur.performance.seance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Référentiel FIGÉ des rôles tenus par le staff sur un bloc (V66). Posé par le super-admin et
 * commun à tous les clubs — comme {@link ReferentielDominante} — pour que les fiches restent
 * comparables d'un club à l'autre.
 */
@Entity
@Table(name = "referentiel_role_bloc")
@Getter
@Setter
public class ReferentielRoleBloc {

    @Id
    @Column(name = "code", length = 20)
    private String code;

    @Column(name = "libelle", nullable = false, length = 60)
    private String libelle;

    /** Pictogramme affiché sur la carte : « ⚖ Karim » se lit d'un coup d'œil, « Karim » non. */
    @Column(name = "icone", nullable = false, length = 8)
    private String icone;

    @Column(name = "ordre", nullable = false)
    private Short ordre = 0;
}
