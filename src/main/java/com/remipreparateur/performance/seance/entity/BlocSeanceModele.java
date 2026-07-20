package com.remipreparateur.performance.seance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bloc d'un MODÈLE de séance : gabarit de {@link BlocSeance}, recopié tel quel à la
 * planification. Même structure, à une nuance près : le staff porté ici est un staff
 * « par défaut » — il est repris sur la séance créée, où il reste modifiable.
 */
@Entity
@Table(name = "bloc_seance_modele")
@Getter
@Setter
public class BlocSeanceModele {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "seance_modele_id", nullable = false)
    private UUID seanceModeleId;

    @Column(name = "ordre", nullable = false)
    private Short ordre = 0;

    @Column(name = "libelle", nullable = false, length = 120)
    private String libelle;

    /** Séquençage structuré libre, ex. « 2 × (4 × 1' + 1') ». Prime sur la durée si renseigné. */
    @Column(name = "sequencage", length = 120)
    private String sequencage;

    @Column(name = "duree_minutes")
    private Short dureeMinutes;

    @Column(name = "zone_terrain", length = 120)
    private String zoneTerrain;

    /** Comptes staff affectés par défaut au bloc. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "bloc_seance_modele_staff", joinColumns = @JoinColumn(name = "bloc_id"))
    @Column(name = "utilisateur_id")
    private List<UUID> staffIds = new ArrayList<>();
}
