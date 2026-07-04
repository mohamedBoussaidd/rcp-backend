package com.remipreparateur.notification.service;

import com.remipreparateur.notification.entity.Priorite;
import com.remipreparateur.notification.entity.TypeNotification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
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

    /**
     * Entretien passé en PARTAGE_JOUEUR → info au joueur. Renvoie {@code true} si une notification
     * a effectivement été délivrée (compte lié), {@code false} sinon (fiche sans compte : aucun envoi,
     * aucune erreur — le partage reste valable, le joueur le verra à l'activation de son compte).
     */
    public boolean entretienPartage(UUID equipeId, UUID joueurId) {
        if (equipeId == null || joueurId == null) return false;
        try {
            return dispatcher.versJoueurFiche(equipeId, joueurId, TypeNotification.ENTRETIEN_PARTAGE,
                    "Entretien partagé", "Le staff a partagé un entretien individuel avec toi.",
                    "/joueur/entretiens", Priorite.NORMALE, null, null, false);
        } catch (Exception ignore) {
            return false;
        }
    }

    /**
     * Rendez-vous d'entretien planifié (ou déplacé si {@code modifie}) → info au joueur.
     * Visibilité agenda ≠ visibilité contenu : on annonce le créneau, jamais les notes.
     * Best-effort comme {@link #entretienPartage} (fiche sans compte : aucun envoi, aucune erreur).
     */
    public boolean entretienPlanifie(UUID equipeId, UUID joueurId, String typeLabel,
                                     LocalDate date, LocalTime heure, boolean modifie) {
        if (equipeId == null || joueurId == null || date == null) return false;
        String quand = date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH))
                + (heure != null ? " à " + heure.format(DateTimeFormatter.ofPattern("HH'h'mm")) : "");
        String corps = modifie
                ? "Ton entretien " + typeLabel + " a été déplacé : " + quand + "."
                : "Un entretien " + typeLabel + " est prévu avec toi " + quand + ".";
        try {
            return dispatcher.versJoueurFiche(equipeId, joueurId, TypeNotification.ENTRETIEN_PLANIFIE,
                    modifie ? "Entretien déplacé" : "Entretien planifié", corps,
                    "/joueur/entretiens", Priorite.NORMALE, null, null, false);
        } catch (Exception ignore) {
            return false;
        }
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
