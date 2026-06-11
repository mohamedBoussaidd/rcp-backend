package com.remipreparateur.performance.seance.dto;

import java.util.UUID;

/** DTOs du catalogue des types de séance + cibles propres au club actif. */
public final class TypeSeanceDtos {

    private TypeSeanceDtos() {}

    /** Type de séance enrichi des cibles du club actif (null si non paramétrées). */
    public record TypeSeanceResponse(
            UUID id,
            String code,
            String libelle,
            String jourSemaine,
            Short intensiteTheorique,
            String objectifPrincipal,
            Short dureeTheoriqueMin,
            Integer objectifDistanceM,
            Integer objectifDistanceHauteIntensiteM,
            Short objectifIntensite) {}

    /** Mise à jour des cibles d'un type pour le club actif (toutes optionnelles). */
    public record CiblesRequest(
            Integer objectifDistanceM,
            Integer objectifDistanceHauteIntensiteM,
            Short objectifIntensite) {}
}
