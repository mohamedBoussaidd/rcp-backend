package com.remipreparateur.entretien.repository;

import com.remipreparateur.entretien.entity.AutoEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AutoEvaluationRepository extends JpaRepository<AutoEvaluation, UUID> {

    List<AutoEvaluation> findByJoueurIdOrderByCreatedAtDesc(UUID joueurId);

    Optional<AutoEvaluation> findFirstByAxeTravailIdOrderByCreatedAtDesc(UUID axeTravailId);

    boolean existsByAxeTravailIdAndCreatedAtAfter(UUID axeTravailId, LocalDateTime apres);
}
