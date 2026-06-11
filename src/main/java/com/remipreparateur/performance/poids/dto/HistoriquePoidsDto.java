package com.remipreparateur.performance.poids.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record HistoriquePoidsDto(
        Long id,
        UUID joueurId,
        LocalDate date,
        BigDecimal poids,
        String commentaire
) {}
