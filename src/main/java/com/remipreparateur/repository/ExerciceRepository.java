package com.remipreparateur.repository;

import com.remipreparateur.entity.Exercice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ExerciceRepository extends JpaRepository<Exercice, UUID> {
    List<Exercice> findByClubIdOrderByCreatedAtDesc(UUID clubId);
}
