package com.remipreparateur.entretien.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Entretien individuel mené par le staff avec un joueur. {@code visibilite} = STAFF par défaut ;
 * le passage à PARTAGE_JOUEUR (action explicite) le rend visible au joueur et le notifie.
 * {@code type} : VIDEO | TERRAIN | DISCUSSION. Liens optionnels vers une séance / un schéma / une vidéo.
 * {@code statut} : PLANIFIE (rendez-vous à venir, heure facultative, visible au calendrier et notifié
 * au joueur — contenu non partagé) | REALISE (compte-rendu, défaut — seul statut compté dans les agrégats).
 */
@Entity
@Table(name = "entretien")
@Getter
@Setter
public class Entretien {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "joueur_id", nullable = false)
    private UUID joueurId;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(name = "equipe_id")
    private UUID equipeId;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "date_entretien", nullable = false)
    private LocalDate dateEntretien;

    @Column(name = "heure")
    private LocalTime heure;

    @Column(name = "statut", nullable = false)
    private String statut = "REALISE";

    @Column(name = "mene_par")
    private UUID menePar;

    @Column(name = "notes")
    private String notes;

    @Column(name = "visibilite", nullable = false)
    private String visibilite = "STAFF";

    @Column(name = "seance_id")
    private UUID seanceId;

    @Column(name = "schema_tactique_id")
    private UUID schemaTactiqueId;

    @Column(name = "video_url")
    private String videoUrl;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
