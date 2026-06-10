package com.remipreparateur.repository;

import com.remipreparateur.entity.MatchPrepa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface MatchPrepaRepository extends JpaRepository<MatchPrepa, UUID> {
    List<MatchPrepa> findByEquipeIdOrderByDateMatchDescCreatedAtDesc(UUID equipeId);
}
