package com.remipreparateur.performance.seance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

/**
 * Référentiel global (seedé en V61) des sous-principes du projet de jeu travaillés en séance.
 * {@code phase} reprend les PhaseKey du moteur tactique (OFF, DEF, T_OD, T_DO) + CPA_OFF /
 * CPA_DEF, qui n'existent que comme référentiel de séance (pas des phases du moteur).
 */
@Entity
@Table(name = "referentiel_sous_principe")
@Getter
@Setter
public class ReferentielSousPrincipe {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "libelle", nullable = false, length = 80)
    private String libelle;

    @Column(name = "phase", nullable = false, length = 10)
    private String phase;

    @Column(name = "ordre", nullable = false)
    private Short ordre = 0;
}
