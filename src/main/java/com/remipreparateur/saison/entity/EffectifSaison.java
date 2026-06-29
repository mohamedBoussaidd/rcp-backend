package com.remipreparateur.saison.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Appartenance d'un joueur à l'effectif d'UNE saison. Distinct du {@code statut} du
 * joueur (dispo : actif/blessé/suspendu/prêté). Un joueur transféré n'est simplement
 * pas réinscrit dans la saison suivante — sa fiche et ses données sont conservées.
 */
@Entity
@Table(name = "effectif_saison")
@Getter
@Setter
public class EffectifSaison {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "saison_id", nullable = false)
    private UUID saisonId;

    /** Équipe à laquelle ce joueur appartient dans la saison (cf. V37). */
    @Column(name = "equipe_id", nullable = false)
    private UUID equipeId;

    @Column(name = "joueur_id", nullable = false)
    private UUID joueurId;

    @Column(name = "date_entree")
    private LocalDate dateEntree;

    @Column(name = "date_sortie")
    private LocalDate dateSortie;

    @Column(name = "numero_maillot")
    private Short numeroMaillot;
}
