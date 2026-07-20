package com.remipreparateur.performance.seance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bloc (temps) d'une séance en mode avancé : regroupe des lignes d'exercices
 * ({@code seance_exercice.bloc_id}) avec un séquençage, une zone du terrain et le staff
 * affecté (comptes staff du club). Sans bloc, la liste plate d'exercices reste la référence.
 */
@Entity
@Table(name = "bloc_seance")
@Getter
@Setter
public class BlocSeance {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "seance_id", nullable = false)
    private UUID seanceId;

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

    /** Comptes staff affectés au bloc (kiné, prépa, adjoint…). */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "bloc_seance_staff", joinColumns = @JoinColumn(name = "bloc_id"))
    @Column(name = "utilisateur_id")
    private List<UUID> staffIds = new ArrayList<>();
}
