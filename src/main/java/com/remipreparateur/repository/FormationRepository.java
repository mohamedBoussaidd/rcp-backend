package com.remipreparateur.repository;

import com.remipreparateur.entity.Formation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface FormationRepository extends JpaRepository<Formation, UUID> {
    List<Formation> findByClubIdOrderByCreatedAtDesc(UUID clubId);
}
