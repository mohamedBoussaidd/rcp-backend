package com.remipreparateur.performance.objectif;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ObjectifHebdoRepository extends JpaRepository<ObjectifHebdo, UUID> {
    Optional<ObjectifHebdo> findByEquipeId(UUID equipeId);
}
