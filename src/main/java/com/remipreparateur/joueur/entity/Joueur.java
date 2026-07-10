package com.remipreparateur.joueur.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "joueur")
@Getter
@Setter
public class Joueur {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "nom", nullable = false)
    private String nom;

    @Column(name = "prenom", nullable = false)
    private String prenom;

    @Column(name = "date_naissance")
    private LocalDate dateNaissance;

    @Column(name = "poids_actuel")
    private BigDecimal poidsActuel;

    @Column(name = "poids_forme_cible")
    private BigDecimal poidsFormeCible;

    @Column(name = "taille")
    private BigDecimal taille;

    @Column(name = "pied_fort")
    private String piedFort;

    @Column(name = "poste_principal")
    private String postePrincipal;

    @Column(name = "poste_secondaire")
    private String posteSecondaire;

    @Column(name = "profil_athletique")
    private String profilAthletique;

    @Column(name = "statut")
    private String statut = "actif";

    @Column(name = "date_arrivee_club")
    private LocalDate dateArriveeClub;

    /**
     * Club de rattachement de la fiche (source de vérité du niveau club). Une fiche existe au
     * niveau du club, indépendamment de son appartenance à une équipe (gérée par effectif_saison).
     */
    @Column(name = "club_id")
    private UUID clubId;

    // Phase 4 : le cache legacy `equipe_id` a été supprimé (V51). L'appartenance d'équipe — multiple
    // et par saison — est portée exclusivement par {@code effectif_saison} (cf. AppartenanceService).
}
