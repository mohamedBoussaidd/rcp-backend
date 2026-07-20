package com.remipreparateur.performance.seance.repository;

import com.remipreparateur.performance.seance.entity.BlocSeanceModele;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BlocSeanceModeleRepository extends JpaRepository<BlocSeanceModele, UUID> {

    List<BlocSeanceModele> findBySeanceModeleIdOrderByOrdreAsc(UUID seanceModeleId);

    void deleteBySeanceModeleId(UUID seanceModeleId);
}
