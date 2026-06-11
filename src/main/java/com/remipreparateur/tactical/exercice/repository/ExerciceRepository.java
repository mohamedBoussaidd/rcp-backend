package com.remipreparateur.tactical.exercice.repository;

import com.remipreparateur.tactical.exercice.entity.Exercice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ExerciceRepository extends JpaRepository<Exercice, UUID> {
    List<Exercice> findByClubIdOrderByCreatedAtDesc(UUID clubId);
}
