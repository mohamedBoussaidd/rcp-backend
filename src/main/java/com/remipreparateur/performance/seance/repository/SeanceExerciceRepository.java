package com.remipreparateur.performance.seance.repository;

import com.remipreparateur.performance.seance.entity.SeanceExercice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface SeanceExerciceRepository extends JpaRepository<SeanceExercice, UUID> {

    List<SeanceExercice> findBySeanceIdOrderByOrdreAsc(UUID seanceId);

    void deleteBySeanceId(UUID seanceId);
}
