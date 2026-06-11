package com.remipreparateur.tactical.plandejeu.repository;

import com.remipreparateur.tactical.plandejeu.entity.PlanDeJeu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanDeJeuRepository extends JpaRepository<PlanDeJeu, UUID> {
    Optional<PlanDeJeu> findByEquipeId(UUID equipeId);
}
