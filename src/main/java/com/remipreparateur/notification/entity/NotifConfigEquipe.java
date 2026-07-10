package com.remipreparateur.notification.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Configuration de notification d'une équipe : seuils configurables (colonnes dédiées),
 * heures des digests matin/soir et paramètres des rappels joueur. Une ligne par équipe ;
 * valeurs par défaut posées en migration. Modifiable par tout le staff de l'équipe.
 */
@Entity
@Table(name = "notif_config_equipe")
@Getter
@Setter
public class NotifConfigEquipe {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "equipe_id", nullable = false)
    private UUID equipeId;

    // ── Seuils de charge / état ──
    @Column(name = "seuil_acwr_haut", nullable = false)
    private BigDecimal seuilAcwrHaut = new BigDecimal("1.50");

    @Column(name = "seuil_acwr_bas", nullable = false)
    private BigDecimal seuilAcwrBas = new BigDecimal("0.80");

    @Column(name = "seuil_readiness_min", nullable = false)
    private BigDecimal seuilReadinessMin = new BigDecimal("50.0");

    // ── Seuils wellness (Hooper, 5 items sur 1..10 ; convention app : 1 = bon … 10 = mauvais
    //    pour TOUS les items). Alerte si valeur >= seuil. ──
    @Column(name = "seuil_wellness_fatigue", nullable = false)
    private short seuilWellnessFatigue = 8;

    @Column(name = "seuil_wellness_douleur", nullable = false)
    private short seuilWellnessDouleur = 8;

    @Column(name = "seuil_wellness_stress", nullable = false)
    private short seuilWellnessStress = 8;

    @Column(name = "seuil_wellness_sommeil", nullable = false)
    private short seuilWellnessSommeil = 8;

    @Column(name = "seuil_wellness_humeur", nullable = false)
    private short seuilWellnessHumeur = 8;

    // ── Seuils de poids (kg) ──
    @Column(name = "seuil_poids_court", nullable = false)
    private BigDecimal seuilPoidsCourt = new BigDecimal("1.0");

    @Column(name = "seuil_poids_moyen", nullable = false)
    private BigDecimal seuilPoidsMoyen = new BigDecimal("3.0");

    // ── Complétion ──
    @Column(name = "seuil_completion_min", nullable = false)
    private short seuilCompletionMin = 70;

    // ── Digests ──
    @Column(name = "digest_actif", nullable = false)
    private boolean digestActif = true;

    @Column(name = "digest_matin_heure", nullable = false)
    private LocalTime digestMatinHeure = LocalTime.of(8, 0);

    @Column(name = "digest_soir_heure", nullable = false)
    private LocalTime digestSoirHeure = LocalTime.of(19, 0);

    // ── Rappels joueur ──
    @Column(name = "rappel_wellness_actif", nullable = false)
    private boolean rappelWellnessActif = true;

    @Column(name = "rappel_wellness_heure", nullable = false)
    private LocalTime rappelWellnessHeure = LocalTime.of(8, 0);

    @Column(name = "rappel_rpe_actif", nullable = false)
    private boolean rappelRpeActif = true;

    @Column(name = "rappel_rpe_delai_heures", nullable = false)
    private short rappelRpeDelaiHeures = 3;

    @Column(name = "rappel_poids_actif", nullable = false)
    private boolean rappelPoidsActif = false;

    @Column(name = "rappel_seance_actif", nullable = false)
    private boolean rappelSeanceActif = true;

    // ── Alerte staff « joueur sans entretien récent » (digest hebdo) ──
    @Column(name = "entretien_alerte_active", nullable = false)
    private boolean entretienAlerteActive = true;

    @Column(name = "entretien_seuil_jours", nullable = false)
    private short entretienSeuilJours = 42;   // 6 semaines

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
