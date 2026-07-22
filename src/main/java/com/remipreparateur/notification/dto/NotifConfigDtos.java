package com.remipreparateur.notification.dto;

import com.remipreparateur.notification.entity.NiveauEnvoi;
import com.remipreparateur.notification.entity.TypeNotification;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** DTOs de configuration des notifications (seuils, digests, rappels, routage, droits). */
public final class NotifConfigDtos {

    private NotifConfigDtos() {}

    /** Config complète de l'équipe (seuils + digests + rappels). Sert en lecture et écriture. */
    public record ConfigDto(
            BigDecimal seuilAcwrHaut,
            BigDecimal seuilAcwrBas,
            BigDecimal seuilReadinessMin,
            short seuilWellnessFatigue,
            short seuilWellnessDouleur,
            short seuilWellnessStress,
            short seuilWellnessSommeil,
            short seuilWellnessHumeur,
            BigDecimal seuilPoidsCourt,
            BigDecimal seuilPoidsMoyen,
            short seuilCompletionMin,
            boolean digestActif,
            LocalTime digestMatinHeure,
            LocalTime digestSoirHeure,
            String digestJours,
            String digestPoidsJours,
            boolean rappelWellnessActif,
            LocalTime rappelWellnessHeure,
            String rappelWellnessJours,
            boolean rappelRpeActif,
            short rappelRpeDelaiHeures,
            boolean rappelSeanceActif) {}

    /** Routage d'un type : rôles destinataires (CSV) + actif. */
    public record RoutageDto(TypeNotification type, String roles, boolean actif) {}

    /** Droit d'émission d'un joueur. */
    public record DroitEnvoiDto(UUID joueurId, String joueurNom, NiveauEnvoi niveau) {}

    public record DroitEnvoiRequest(NiveauEnvoi niveau) {}

    /** Préférence d'un type pour un destinataire (vue + édition). */
    public record PreferenceDto(
            TypeNotification type,
            String categorie,
            boolean actif,
            boolean verrouilleParStaff,
            /** false si le destinataire ne peut pas modifier (verrouillé par le staff). */
            boolean modifiable) {}

    /** Mise à jour d'une préférence par le destinataire lui-même. */
    public record PreferenceMeRequest(TypeNotification type, boolean actif) {}

    /** Mise à jour d'une préférence d'un joueur par le staff (actif + verrou). */
    public record PreferenceStaffRequest(TypeNotification type, boolean actif, boolean verrouilleParStaff) {}

    /** Réponse groupée des préférences d'un destinataire. */
    public record PreferencesResponse(List<PreferenceDto> preferences) {}

    /** Ligne de la matrice « par joueur » : un joueur + ses types actifs. */
    public record LigneJoueurDto(UUID joueurId, String nom, java.util.Map<String, Boolean> actifs) {}

    /** Matrice des préférences de l'équipe : types en colonnes, joueurs en lignes. */
    public record EquipeMatriceDto(List<String> types, List<LigneJoueurDto> joueurs) {}

    /** Activer/couper un type pour toute l'équipe d'un coup. */
    public record BulkTypeRequest(TypeNotification type, boolean actif) {}
}
