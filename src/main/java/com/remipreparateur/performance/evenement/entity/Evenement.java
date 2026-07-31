package com.remipreparateur.performance.evenement.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Événement extrasportif du club (réunion, déplacement, examens, vie de club…).
 *
 * <p>Délibérément SÉPARÉ de {@code Seance} : une séance porte la charge, le GPS, la présence
 * et le RPE. Un repas d'équipe ne doit rien peser dans l'ACWR. Voir V92 pour le raisonnement.
 *
 * <p>Sans personne ciblée, l'événement concerne toute l'équipe ; sinon il ne concerne que les
 * fiches listées (joueurs comme staff, tous portés par la table {@code joueur}).
 */
@Entity
@Table(name = "evenement")
@Getter
@Setter
public class Evenement {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    /** null = événement de club, toutes équipes confondues. */
    @Column(name = "equipe_id")
    private UUID equipeId;

    /** VIE_CLUB | DEPLACEMENT | SCOLAIRE | CONVIVIALITE | RENDEZ_VOUS | INDISPONIBILITE | AUTRE. */
    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "titre", nullable = false)
    private String titre;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    /** Fin d'un événement sur plusieurs jours (stage, vacances scolaires). null = 1 jour. */
    @Column(name = "date_fin")
    private LocalDate dateFin;

    @Column(name = "heure_debut")
    private LocalTime heureDebut;

    @Column(name = "heure_fin")
    private LocalTime heureFin;

    @Column(name = "lieu")
    private String lieu;

    @Column(name = "description")
    private String description;

    @Column(name = "visible_joueurs", nullable = false)
    private boolean visibleJoueurs = true;

    @Column(name = "cree_par")
    private UUID creePar;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Personnes nommément concernées (vide = toute l'équipe). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "evenement_joueur", joinColumns = @JoinColumn(name = "evenement_id"))
    @Column(name = "joueur_id")
    private Set<UUID> joueurIds = new LinkedHashSet<>();

    /** Dernier jour couvert par l'événement (dateFin si renseignée, sinon date). */
    public LocalDate dernierJour() {
        return dateFin != null ? dateFin : date;
    }
}
