package com.remipreparateur.tactical.formation.repository;

import com.remipreparateur.tactical.formation.entity.Formation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface FormationRepository extends JpaRepository<Formation, UUID> {
    List<Formation> findByClubIdOrderByCreatedAtDesc(UUID clubId);
}
