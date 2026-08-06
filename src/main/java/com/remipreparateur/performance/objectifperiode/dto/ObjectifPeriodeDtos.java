package com.remipreparateur.performance.objectifperiode.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Contrats d'échange des modèles d'objectif et de leur instanciation sur une période (lot 2). */
public final class ObjectifPeriodeDtos {

    private ObjectifPeriodeDtos() {}

    // ── Modèles réutilisables du club ───────────────────────────────────────

    /** Niveau d'une métrique sur une phase, en % de la cible du référentiel. */
    public record PhaseValeurDto(String metrique, Integer pctDebut, Integer pctFin, String priorite) {}

    public record PhaseDto(UUID id, int ordre, String nom, int poidsDuree,
                           List<PhaseValeurDto> valeurs) {}

    public record ModeleResume(UUID id, String nom, String typePeriode, int nbPhases,
                               long nbUtilisations, LocalDateTime updatedAt) {}

    public record ModeleDetail(ModeleResume entete, List<PhaseDto> phases) {}

    public record ModeleRequest(String nom, String typePeriode, List<PhaseDto> phases) {}

    // ── Objectif accroché à une période ─────────────────────────────────────

    public record ValeurPeriodeDto(Short noSemaine, LocalDate dateLundi, String poste,
                                   String metrique, Integer valeurMin, Integer valeurMax,
                                   String priorite, String phaseNom, boolean modifieManuellement) {}

    public record ObjectifPeriodeResume(UUID id, UUID periodeId, String periodeLibelle,
                                        String typePeriode, LocalDate dateDebut, LocalDate dateFin,
                                        int nbSemaines, UUID modeleId, String modeleNom,
                                        UUID referentielId, String referentielNom,
                                        String phasesResume, String avertissement,
                                        LocalDateTime updatedAt) {}

    public record ObjectifPeriodeDetail(ObjectifPeriodeResume entete, List<ValeurPeriodeDto> valeurs) {}

    /** Instanciation d'un modèle sur une période. Le référentiel est résolu si non fourni. */
    public record InstancierRequest(UUID periodeId, UUID modeleId, UUID referentielId) {}

    /**
     * Aperçu SANS ÉCRITURE : ce que donnerait l'instanciation, avertissement compris.
     * Permet de montrer « 3 semaines pour 4 phases, le Pic sera supprimé » avant de valider.
     */
    public record ApercuResponse(int nbSemaines, String phasesResume, String avertissement,
                                 List<ValeurPeriodeDto> valeurs) {}

    /** État d'une période dans le hub : « objectifs définis » ou « à définir ». */
    public record EtatPeriodeDto(UUID periodeId, String libelle, String typePeriode,
                                 LocalDate dateDebut, LocalDate dateFin, int nbSemaines,
                                 boolean objectifsDefinis, UUID objectifId, String modeleNom) {}
}
