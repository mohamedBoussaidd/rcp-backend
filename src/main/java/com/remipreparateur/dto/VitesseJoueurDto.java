package com.remipreparateur.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Fiche vitesse d'un joueur (km/h) pour l'animation des schémas tactiques. */
public record VitesseJoueurDto(UUID joueurId, BigDecimal vmaxKmh, BigDecimal vmoyKmh) {
}
