package com.remipreparateur.performance.seance.entity;

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

    /**
     * Nature du type — ce que l'application doit ATTENDRE d'une séance de ce type (V93) :
     * {@code TERRAIN} (distance, haute intensité, GPS), {@code MUSCULATION} (charge interne
     * seulement, jamais de mètres) ou {@code SANS_CHARGE_EXTERNE} (piscine, vidéo, récupération).
     *
     * <p>Sans ce champ, tout le code postulait « une séance = du déplacement mesuré au GPS » :
     * le formulaire réclamait une distance attendue pour une séance en salle et la simulation
     * annonçait des kilomètres pour des squats.
     */
    @Column(name = "profil", nullable = false)
    private String profil = "TERRAIN";

    /** Couleur du type dans le calendrier. Était codée en dur côté front, donc absente
     *  pour tout type non prévu à l'avance. */
    @Column(name = "couleur")
    private String couleur;

    /** Ce type attend-il des données de déplacement (distance, haute intensité, sprints) ? */
    public boolean attendGps() {
        return "TERRAIN".equals(profil);
    }
}
