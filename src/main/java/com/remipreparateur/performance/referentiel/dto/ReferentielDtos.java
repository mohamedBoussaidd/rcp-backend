package com.remipreparateur.performance.referentiel.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Contrats d'échange du référentiel de charge (lot 1). */
public final class ReferentielDtos {

    private ReferentielDtos() {}

    // ── Vocabulaire (envoyé une fois, le front boucle dessus) ────────────────

    public record MetriqueDto(String code, String libelle, String unite, String nature,
                              boolean principale, int ordre) {}

    public record PosteDto(String code, String libelle, int ordre) {}

    /** Vocabulaire + catalogue visible : tout ce qu'il faut pour peindre l'écran d'un coup. */
    public record CatalogueResponse(List<MetriqueDto> metriques, List<PosteDto> postes,
                                    List<String> contextes, List<ReferentielResume> referentiels) {}

    // ── Référentiel ─────────────────────────────────────────────────────────

    /** Une case : poste × contexte × métrique → fourchette. */
    public record ValeurDto(String poste, String contexte, String metrique,
                            Integer valeurMin, Integer valeurMax) {}

    public record ReferentielResume(UUID id, UUID clubId, String nom, String niveau, int version,
                                    String statut, boolean plateforme, boolean modifiable,
                                    UUID sourceId, UUID parentId, long nbAdoptions,
                                    LocalDateTime updatedAt) {}

    public record ReferentielDetail(ReferentielResume entete, List<ValeurDto> valeurs) {}

    /** Création d'un brouillon ou renommage ; les valeurs sont facultatives à la création. */
    public record ReferentielRequest(String nom, String niveau, List<ValeurDto> valeurs) {}

    /** Duplication : d'un référentiel plateforme vers un autre, ou vers une copie de club. */
    public record DuplicationRequest(UUID sourceId, String nom, String niveau) {}

    // ── Adoption par un club ────────────────────────────────────────────────

    public record AdoptionRequest(UUID equipeId, UUID referentielId) {}

    /**
     * Une adoption, avec le signal de version. {@code versionDisponibleId} non nul = une version
     * plus récente du même niveau est publiée ; le club migre quand il veut, jamais dans son dos.
     */
    public record AdoptionDto(UUID id, UUID equipeId, String equipeNom,
                              UUID referentielId, String referentielNom, int version,
                              UUID versionDisponibleId, String versionDisponibleNom) {}

    /** Le référentiel réellement appliqué à une équipe, après cascade équipe → club. */
    public record ResolutionDto(UUID equipeId, ReferentielResume referentiel, String origine) {}

    // ── Écart entre deux référentiels (diff de migration, ou écart à la source) ──

    public record EcartLigne(String poste, String contexte, String metrique,
                             Integer avantMin, Integer avantMax,
                             Integer apresMin, Integer apresMax) {}

    public record EcartResponse(UUID avantId, String avantNom, UUID apresId, String apresNom,
                                List<EcartLigne> lignes) {}

    // ── Usage du catalogue (super-admin) ────────────────────────────────────

    /** Combien de clubs sont épinglés sur chaque référentiel — repère les versions oubliées. */
    public record UsageDto(UUID referentielId, String nom, String niveau, int version,
                           String statut, long nbClubs) {}

    public record ClubUtilisateurDto(UUID clubId, String clubNom, UUID equipeId, String equipeNom) {}
}
