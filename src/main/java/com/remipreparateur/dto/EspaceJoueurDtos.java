package com.remipreparateur.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** DTOs de l'espace joueur (donnees personnelles du joueur connecte). */
public final class EspaceJoueurDtos {

    private EspaceJoueurDtos() {}

    public record MaPeseeResponse(
            LocalDate date,
            BigDecimal poids,
            String commentaire) {}
}
