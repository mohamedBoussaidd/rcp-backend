package com.remipreparateur.performance.seance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bloc (temps) d'une séance en mode avancé : regroupe des lignes d'exercices
 * ({@code seance_exercice.bloc_id}) avec un séquençage, des zones du terrain et le staff
 * affecté (comptes staff du club). Sans bloc, la liste plate d'exercices reste la référence.
 *
 * <p>V66 : la zone est passée d'un texte libre à un ensemble de {@link #zones} parmi 8. C'est ce
 * qui permet de détecter que deux blocs simultanés occupent le même espace — impossible avec du
 * texte. Les rôles du staff vivent dans {@link BlocSeanceStaffRole}, à part de l'affectation.
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

    /** ECHAUFFEMENT / SITUATION / JEU / RETOUR_AU_CALME — le moment de séance, nullable. */
    @Column(name = "type", length = 20)
    private String type;

    /** Séquençage structuré libre, ex. « 2 × (4 × 1' + 1') ». Prime sur la durée si renseigné. */
    @Column(name = "sequencage", length = 120)
    private String sequencage;

    /** Pré-remplie par la somme des exercices du bloc côté front, mais toujours corrigeable. */
    @Column(name = "duree_minutes")
    private Short dureeMinutes;

    /** Zones occupées (1..8). Un demi-terrain = 4 zones, un jeu réduit = 1 seule. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "bloc_seance_zone", joinColumns = @JoinColumn(name = "bloc_id"))
    @Column(name = "zone")
    private List<Short> zones = new ArrayList<>();

    /** Comptes staff affectés au bloc (kiné, prépa, adjoint…). */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "bloc_seance_staff", joinColumns = @JoinColumn(name = "bloc_id"))
    @Column(name = "utilisateur_id")
    private List<UUID> staffIds = new ArrayList<>();

    /**
     * Rôles tenus par le staff sur ce bloc (0 à n par personne : celui qui mène arbitre souvent
     * aussi). Table séparée de l'affectation pour ne pas avoir à inventer un rôle aux
     * affectations antérieures à V66 — elles restent « présent, rôle non précisé ».
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "bloc_seance_staff_role", joinColumns = @JoinColumn(name = "bloc_id"))
    private List<BlocSeanceStaffRole> staffRoles = new ArrayList<>();
}
