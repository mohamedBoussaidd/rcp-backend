package com.remipreparateur.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "type_seance")
@Getter
@Setter
public class TypeSeance {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "libelle", nullable = false)
    private String libelle;

    @Column(name = "jour_semaine")
    private String jourSemaine;

    @Column(name = "intensite_theorique")
    private Short intensiteTheorique;

    @Column(name = "objectif_principal")
    private String objectifPrincipal;

    @Column(name = "duree_theorique_min")
    private Short dureeTheoriqueMin;
}
