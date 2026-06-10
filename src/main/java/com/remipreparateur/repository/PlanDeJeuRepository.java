package com.remipreparateur.repository;

import com.remipreparateur.entity.PlanDeJeu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanDeJeuRepository extends JpaRepository<PlanDeJeu, UUID> {
    Optional<PlanDeJeu> findByEquipeId(UUID equipeId);
}
