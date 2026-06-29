package com.remipreparateur.medical.blessure.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "blessure")
@Getter
@Setter
public class Blessure {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "joueur_id", nullable = false)
    private UUID joueurId;

    @Column(name = "blessure_precedente_id")
    private UUID blessurePrecedenteId;

    @Column(name = "date_blessure", nullable = false)
    private LocalDate dateBlessure;

    @Column(name = "date_retour_effectif")
    private LocalDate dateRetourEffectif;

    /** Estimation du retour (le retour réel reste dateRetourEffectif). */
    @Column(name = "date_retour_prevue")
    private LocalDate dateRetourPrevue;

    /** Cycle clinique : INDISPONIBLE -> EN_REPRISE -> RETABLI. */
    @Column(name = "statut", nullable = false)
    private String statut = "INDISPONIBLE";

    @Column(name = "type_blessure")
    private String typeBlessure;

    @Column(name = "zone_corporelle")
    private String zoneCorporelle;

    @Column(name = "cote")
    private String cote;

    @Column(name = "gravite")
    private String gravite;

    @Column(name = "cause_probable")
    private String causeProbable;

    @Column(name = "recidive")
    private boolean recidive = false;

    @Column(name = "commentaire")
    private String commentaire;

    /** Notes réservées au staff médical (distinct du commentaire général). */
    @Column(name = "notes_medicales")
    private String notesMedicales;

    @Column(name = "equipe_id")
    private UUID equipeId;

    /**
     * Vrai pour les retours saisis/validés par le staff. Mis à false quand le scheduler
     * solde AUTOMATIQUEMENT une blessure dont la date de retour prévue est dépassée :
     * le joueur redevient dispo mais le staff doit confirmer (ou prolonger).
     */
    @Column(name = "retour_confirme", nullable = false)
    private boolean retourConfirme = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
