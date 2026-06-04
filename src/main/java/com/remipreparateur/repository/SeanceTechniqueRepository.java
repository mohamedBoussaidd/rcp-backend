package com.remipreparateur.repository;

import com.remipreparateur.entity.SeanceTechnique;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface SeanceTechniqueRepository extends JpaRepository<SeanceTechnique, UUID> {
    List<SeanceTechnique> findByEquipeIdInOrderByDateDesc(Collection<UUID> equipeIds);
    List<SeanceTechnique> findByEquipeIdInAndDateBetweenOrderByDateAsc(
            Collection<UUID> equipeIds, LocalDate debut, LocalDate fin);
}
