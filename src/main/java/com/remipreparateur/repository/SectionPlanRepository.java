package com.remipreparateur.repository;

import com.remipreparateur.entity.SectionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface SectionPlanRepository extends JpaRepository<SectionPlan, UUID> {
    List<SectionPlan> findByPlanDeJeuIdOrderByOrdreAsc(UUID planDeJeuId);
}
