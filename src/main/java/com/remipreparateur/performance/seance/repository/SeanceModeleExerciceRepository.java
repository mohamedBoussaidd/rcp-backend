package com.remipreparateur.performance.seance.repository;

import com.remipreparateur.performance.seance.entity.SeanceModeleExercice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface SeanceModeleExerciceRepository extends JpaRepository<SeanceModeleExercice, UUID> {

    List<SeanceModeleExercice> findBySeanceModeleIdOrderByOrdreAsc(UUID seanceModeleId);

    void deleteBySeanceModeleId(UUID seanceModeleId);
}
