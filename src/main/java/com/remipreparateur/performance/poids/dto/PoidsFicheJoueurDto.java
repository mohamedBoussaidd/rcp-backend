package com.remipreparateur.performance.poids.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PoidsFicheJoueurDto(
        UUID joueurId,
        String nom,
        String prenom,
        String postePrincipal,
        BigDecimal poidsFormeCible,
        LocalDate dernierePeseeDate,
        BigDecimal dernierPoids,
        BigDecimal ecartKg
) {}
