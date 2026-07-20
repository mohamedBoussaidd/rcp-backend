package com.remipreparateur.performance.seance.repository;

import com.remipreparateur.performance.seance.entity.GroupeSeance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GroupeSeanceRepository extends JpaRepository<GroupeSeance, UUID> {

    List<GroupeSeance> findBySeanceIdOrderByOrdreAsc(UUID seanceId);

    void deleteBySeanceId(UUID seanceId);
}
