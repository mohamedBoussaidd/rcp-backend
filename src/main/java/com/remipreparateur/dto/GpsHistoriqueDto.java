package com.remipreparateur.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record GpsHistoriqueDto(
        UUID seanceId,
        LocalDate date,
        String typeCode,
        String typeLibelle,
        Short dureeMinutes,
        BigDecimal distanceTotaleM,
        BigDecimal distance15kmhM,
        BigDecimal distance19kmhM,
        BigDecimal distanceSprint24kmhM,
        BigDecimal distanceSprint28kmhM,
        Short nbSprints24kmh,
        BigDecimal vitesseMaxKmh,
        Short nbAccelerations,
        Short nbFreinages,
        BigDecimal ratioDistanceMin,
        String conditionsMeteo,
        Short temperature
) {}
