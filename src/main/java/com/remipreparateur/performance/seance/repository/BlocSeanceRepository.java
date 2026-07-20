package com.remipreparateur.performance.seance.repository;

import com.remipreparateur.performance.seance.entity.BlocSeance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BlocSeanceRepository extends JpaRepository<BlocSeance, UUID> {

    List<BlocSeance> findBySeanceIdOrderByOrdreAsc(UUID seanceId);

    void deleteBySeanceId(UUID seanceId);
}
