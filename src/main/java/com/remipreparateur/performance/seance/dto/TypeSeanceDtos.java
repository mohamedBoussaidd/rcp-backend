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
            /** Échelle 1..5 (contrainte en base) — et non un pourcentage. */
            Short intensiteTheorique,
            String objectifPrincipal,
            Short dureeTheoriqueMin,
            /** TERRAIN | MUSCULATION | SANS_CHARGE_EXTERNE (V93). */
            String profil,
            /** Couleur du type dans le calendrier — désormais portée par la base. */
            String couleur,
            Integer objectifDistanceM,
            Integer objectifDistanceHauteIntensiteM,
            Short objectifIntensite) {}

    /**
     * Réglages d'un type POUR LE CLUB ACTIF (toutes optionnelles) : cibles physiques et,
     * depuis V94, couleur du calendrier. Rien ici ne sort du club.
     */
    public record CiblesRequest(
            Integer objectifDistanceM,
            Integer objectifDistanceHauteIntensiteM,
            Short objectifIntensite,
            /** Couleur hexadécimale (#RRGGBB) propre au club ; null = défaut du catalogue. */
            String couleur) {}

    /**
     * Nature d'un type de séance. <b>Le catalogue des types est GLOBAL</b> (aucun
     * {@code club_id}) : ce réglage vaut pour toute la plateforme, il est donc réservé au
     * SUPER_ADMIN. La couleur, elle, est passée par club (cf. {@link CiblesRequest}).
     */
    public record ApparenceRequest(
            /** TERRAIN | MUSCULATION | SANS_CHARGE_EXTERNE. */
            String profil) {}
}
