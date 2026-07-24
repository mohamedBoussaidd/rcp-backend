package com.remipreparateur.ia.repository;

import com.remipreparateur.ia.entity.IaUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.UUID;

public interface IaUsageRepository extends JpaRepository<IaUsage, UUID> {

    /** Nb d'appels d'une feature pour un club à une date (décompte du quota quotidien). */
    long countByClubIdAndFeatureAndJour(UUID clubId, String feature, LocalDate jour);
}
