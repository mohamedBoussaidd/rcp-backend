package com.remipreparateur.performance.seance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Groupe du jour d'une séance (équipe couleur ou groupe libre), défini pour toute la séance
 * ({@code blocId} null) ou pour un bloc précis (résolution : groupes du bloc s'il en existe,
 * sinon groupes globaux). Les groupes Blessés / Réathlétisation / Absents sont CALCULÉS à la
 * volée (modules médical / RTP / appel), jamais stockés ici.
 */
@Entity
@Table(name = "groupe_seance")
@Getter
@Setter
public class GroupeSeance {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "seance_id", nullable = false)
    private UUID seanceId;

    @Column(name = "bloc_id")
    private UUID blocId;

    /** COULEUR (équipe bleue/rouge/jaune…) ou LIBRE (groupe de travail nommé). */
    @Column(name = "type", nullable = false, length = 12)
    private String type = "LIBRE";

    @Column(name = "libelle", nullable = false, length = 80)
    private String libelle;

    @Column(name = "couleur", length = 20)
    private String couleur;

    @Column(name = "ordre", nullable = false)
    private Short ordre = 0;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "groupe_seance_joueur", joinColumns = @JoinColumn(name = "groupe_id"))
    @Column(name = "joueur_id")
    private List<UUID> joueurIds = new ArrayList<>();
}
