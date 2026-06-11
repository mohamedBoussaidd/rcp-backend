package com.remipreparateur.medical.rtp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Étape du protocole de retour au jeu (RTP) d'une blessure.
 * statut : A_FAIRE -> EN_COURS -> VALIDEE ; progression = validées / total.
 */
@Entity
@Table(name = "rtp_etape")
@Getter
@Setter
public class RtpEtape {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "blessure_id", nullable = false)
    private UUID blessureId;

    @Column(name = "ordre", nullable = false)
    private Short ordre;

    @Column(name = "libelle", nullable = false)
    private String libelle;

    @Column(name = "statut", nullable = false)
    private String statut = "A_FAIRE";

    @Column(name = "date_validation")
    private LocalDate dateValidation;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
