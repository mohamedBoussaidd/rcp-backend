package com.remipreparateur.club.pack;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** DTOs de la couche packs / modules (abonnement des clubs). */
public final class PackDtos {

    private PackDtos() {
    }

    /** Un module du catalogue (pour la matrice de l'UI). */
    public record ModuleDto(String code, String libelle, String description, boolean socle, int ordre) {
    }

    /** Un pack commercial et ses modules. */
    public record PackDto(String code, String libelle, String description, BigDecimal prixMensuel,
                          int ordre, boolean actif, boolean predefini, List<String> modules) {
    }

    /** Création / édition d'un pack (le prix est saisi manuellement par le super-admin). */
    public record PackUpsertRequest(String libelle, String description, BigDecimal prixMensuel,
                                    Integer ordre, Boolean actif, Set<String> modules) {
    }

    /** État d'un module pour un club : actif ou non, et d'où vient l'activation. */
    public record ClubModuleEtatDto(String code, String libelle, String description,
                                    boolean socle, boolean actif, String source) {
    }

    /** Abonnement complet d'un club : son pack + l'état de chaque module. */
    public record ClubAbonnementDto(UUID clubId, String packCode, List<ClubModuleEtatDto> modules) {
    }

    public record AssignerPackRequest(String packCode) {
    }

    public record DefinirModuleRequest(boolean actif) {
    }
}
