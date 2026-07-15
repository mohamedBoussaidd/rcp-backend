package com.remipreparateur.medical.protocole.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

/** Étape type d'un {@link ProtocoleModele} (miroir de RtpEtape, sans statut ni validation). */
@Entity
@Table(name = "protocole_modele_etape")
@Getter
@Setter
public class ProtocoleModeleEtape {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "modele_id", nullable = false)
    private UUID modeleId;

    @Column(name = "ordre", nullable = false)
    private short ordre;

    @Column(name = "libelle", nullable = false)
    private String libelle;

    /** Fenêtre indicative de la phase, en jours depuis la blessure (ex. J1–J5). */
    @Column(name = "j_debut")
    private Short jDebut;

    @Column(name = "j_fin")
    private Short jFin;

    @Column(name = "description")
    private String description;
}
