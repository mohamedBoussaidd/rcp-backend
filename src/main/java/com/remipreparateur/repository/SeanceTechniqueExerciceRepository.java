package com.remipreparateur.repository;

import com.remipreparateur.entity.SeanceTechniqueExercice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface SeanceTechniqueExerciceRepository extends JpaRepository<SeanceTechniqueExercice, UUID> {
    List<SeanceTechniqueExercice> findBySeanceTechniqueIdOrderByOrdreAsc(UUID seanceTechniqueId);
    void deleteBySeanceTechniqueId(UUID seanceTechniqueId);
}
