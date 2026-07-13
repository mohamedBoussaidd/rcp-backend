package com.remipreparateur.performance.seance.repository;

import com.remipreparateur.performance.seance.entity.SeanceModele;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface SeanceModeleRepository extends JpaRepository<SeanceModele, UUID> {

    List<SeanceModele> findByClubIdOrderByCreatedAtDesc(UUID clubId);
}
