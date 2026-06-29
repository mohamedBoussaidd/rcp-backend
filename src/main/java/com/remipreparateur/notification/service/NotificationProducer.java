package com.remipreparateur.notification.service;

import com.remipreparateur.notification.entity.Priorite;
import com.remipreparateur.notification.entity.TypeNotification;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Producteur d'événements métier → notifications. Point d'entrée appelé par les autres
 * modules (médical, séances, comptes) pour émettre une notification sans connaître la
 * mécanique de routage/fan-out. Robuste : une erreur d'émission ne doit pas faire échouer
 * l'action métier appelante (try/catch côté appelant ou ici).
 */
@Service
public class NotificationProducer {

    private final NotificationDispatcher dispatcher;

    public NotificationProducer(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    /** Gêne / blessure déclarée par un joueur → alerte URGENTE au staff médical. */
    public void geneDeclaree(UUID equipeId, UUID joueurId, String joueurNom, String zone, Short intensite) {
        if (equipeId == null) return;
        String corps = joueurNom + " signale une gêne"
                + (zone != null ? " : " + zone : "")
                + (intensite != null ? " (intensité " + intensite + "/10)" : "");
        safe(() -> dispatcher.versStaff(equipeId, TypeNotification.ALERTE_GENE,
                "Gêne signalée", corps, "/suivi-subjectif", joueurId, Priorite.URGENTE));
    }

    /** Nouveau document médical concernant un joueur → info au joueur. */
    public void documentMedicalAjoute(UUID equipeId, UUID joueurId, String titreDoc) {
        if (equipeId == null || joueurId == null) return;
        safe(() -> dispatcher.versJoueurFiche(equipeId, joueurId, TypeNotification.DOC_MEDICAL,
                "Nouveau document médical", titreDoc, "/joueur/documents", Priorite.NORMALE,
                null, null, false));
    }

    /** Séance créée/modifiée/annulée → info aux joueurs de l'équipe. */
    public void seanceModifiee(UUID equipeId, String resume) {
        if (equipeId == null) return;
        safe(() -> dispatcher.versEquipeJoueurs(equipeId, TypeNotification.SEANCE_MODIFIEE,
                "Mise à jour de séance", resume, "/joueur", null, null, false));
    }

    /** Prépa d'un match partagée (publiée) → info aux joueurs de l'équipe. */
    public void matchPublie(UUID equipeId, String adversaire) {
        if (equipeId == null) return;
        String corps = "Le staff a partagé la prépa du match"
                + (adversaire != null && !adversaire.isBlank() ? " contre " + adversaire : "");
        safe(() -> dispatcher.versEquipeJoueurs(equipeId, TypeNotification.MATCH_PARTAGE,
                "Match partagé", corps, "/joueur/matchs", null, null, false));
    }

    /** Absence/retard déclaré(e) par un joueur depuis la PWA (appel) → info au staff. */
    public void absenceDeclaree(UUID equipeId, UUID joueurId, String joueurNom, String statutLabel, String quand) {
        if (equipeId == null) return;
        String corps = joueurNom + " se déclare " + statutLabel + (quand != null ? quand : "");
        safe(() -> dispatcher.versStaff(equipeId, TypeNotification.ALERTE_STATUT,
                "Présence : auto-déclaration", corps, "/dashboard", joueurId, Priorite.NORMALE));
    }

    /** Changement de statut d'un joueur (indisponible…) → alerte au staff. */
    public void statutJoueurChange(UUID equipeId, UUID joueurId, String joueurNom, String statut) {
        if (equipeId == null) return;
        safe(() -> dispatcher.versStaff(equipeId, TypeNotification.ALERTE_STATUT,
                "Statut joueur", joueurNom + " : " + statut, "/etat-effectif", joueurId, Priorite.NORMALE));
    }

    private void safe(Runnable r) {
        try { r.run(); } catch (Exception ignore) { /* émission best-effort */ }
    }
}
