package com.remipreparateur.tactical.match.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** DTOs du module Match (cycle de vie avant/après), niveau équipe. */
public final class MatchDtos {

    private MatchDtos() {}

    // ── Liste (cartes) ──
    public record MatchResume(
            UUID id,
            String adversaire,
            LocalDate dateMatch,
            String competition,
            boolean domicile,
            String resultat,
            String score,
            boolean gpsLie,
            boolean publie) {}

    // ── Détail complet ──
    public record MatchResponse(
            UUID id,
            UUID equipeId,
            boolean modifiable,
            String adversaire,
            LocalDate dateMatch,
            String competition,
            boolean domicile,
            String consignes,
            // Logistique
            String lieuRdv,
            LocalTime heureRdv,
            LocalTime heureMatch,
            String couleurMaillot,
            String infosLogistiques,
            // Publication
            boolean publie,
            LocalDateTime publieAt,
            boolean compoVisible,
            String resultat,
            String score,
            String notesDebrief,
            UUID sessionGpsId,
            List<SchemaResponse> schemas,
            List<CompoItemResponse> compo,
            List<SurveilleResponse> surveilles,
            List<UUID> suspendus,
            LocalDateTime updatedAt) {}

    // ── Création / mise à jour ──
    public record MatchCreateRequest(
            @NotBlank String adversaire,
            LocalDate dateMatch,
            String competition,
            boolean domicile) {}

    /** Bloc AVANT (prépa) : infos de match + consignes + logistique. */
    public record MatchInfosRequest(
            @NotBlank String adversaire,
            LocalDate dateMatch,
            String competition,
            boolean domicile,
            String consignes,
            String lieuRdv,
            LocalTime heureRdv,
            LocalTime heureMatch,
            String couleurMaillot,
            String infosLogistiques) {}

    /** Publication vers les joueurs + réglage de visibilité de la compo. */
    public record PublicationRequest(
            boolean publie,
            boolean compoVisible) {}

    /** Liste des joueurs suspendus pour ce match (remplace l'existant). */
    public record SuspendusRequest(
            List<UUID> joueurIds) {}

    /** Bloc APRÈS (débrief) : résultat, score, notes. */
    public record MatchDebriefRequest(
            String resultat,
            String score,
            String notesDebrief) {}

    // ── Schémas adverses (copie) ──
    public record SchemaRequest(
            String titre,
            @NotBlank String schemaJson,
            String apercu) {}

    public record SchemaResponse(
            UUID id,
            String titre,
            String schemaJson,
            String apercu,
            int ordre) {}

    // ── Compo ──
    public record CompoItemRequest(
            @NotNull UUID joueurId,
            BigDecimal x,
            BigDecimal y,
            String statut,
            String consigne) {}

    public record CompoUpdateRequest(
            @NotNull List<CompoItemRequest> placements) {}

    public record CompoItemResponse(
            UUID joueurId,
            String nom,
            String prenom,
            String postePrincipal,
            BigDecimal x,
            BigDecimal y,
            String statut,
            String consigne) {}

    // ── Joueurs à surveiller ──
    public record SurveilleRequest(
            String cible,        // ADVERSE | EQUIPE
            UUID joueurId,       // si EQUIPE
            String nom,          // si ADVERSE (nom libre)
            String note) {}

    public record SurveilleResponse(
            UUID id,
            String cible,
            UUID joueurId,
            String nom,
            String note) {}

    // ── Lien session GPS ──
    public record SessionGpsRequest(UUID sessionGpsId) {}

    /** Option du sélecteur de session GPS (séances de l'équipe). */
    public record SessionGpsOption(
            UUID id,
            LocalDate date,
            String libelle) {}

    /** Récap des apparitions d'un joueur par statut, sur tous les matchs de l'équipe. */
    public record JoueurCompoStats(
            UUID joueurId,
            String nom,
            String prenom,
            String postePrincipal,
            long titulaire,
            long remplacant,
            long reserve,
            long repos,
            long suspendu,
            long total) {}

    /** Charge GPS d'un joueur issue de la session liée (tableau de débrief). */
    public record ChargeJoueur(
            UUID joueurId,
            String nom,
            String prenom,
            Short dureeMinutes,
            BigDecimal distanceTotaleM,
            BigDecimal distanceSprint24kmhM,
            Short nbSprints24kmh,
            BigDecimal vitesseMaxKmh) {}

    // ════════════ Lecture côté JOUEUR (PWA, matchs publiés) ════════════

    /** Carte d'un match publié, vue joueur. */
    public record MatchJoueurResume(
            UUID id,
            String adversaire,
            LocalDate dateMatch,
            LocalTime heureMatch,
            String competition,
            boolean domicile,
            String monStatut) {}   // null = non convoqué

    /** Identité minimale d'un joueur (non convoqués affichés au joueur si compo complète). */
    public record NomJoueur(
            UUID joueurId,
            String nom,
            String prenom,
            String postePrincipal) {}

    /** Détail d'un match publié, vue joueur (lecture seule). */
    public record MatchJoueurDetail(
            UUID id,
            String adversaire,
            String monEquipeNom,               // nom du club (équipe du joueur), pour le résumé VS
            LocalDate dateMatch,
            LocalTime heureMatch,
            String competition,
            boolean domicile,
            String lieuRdv,
            LocalTime heureRdv,
            String couleurMaillot,
            String infosLogistiques,
            String consignes,
            String monStatut,                  // null = non convoqué
            String maConsigne,                 // consigne perso ; null → le joueur lit les consignes d'équipe
            boolean compoVisible,
            List<CompoItemResponse> compo,     // positions masquées (x=y=0) si compo non visible
            List<NomJoueur> nonConvoques,      // peuplé seulement si compo visible
            List<SchemaResponse> schemas,
            List<SurveilleResponse> surveilles) {}
}
