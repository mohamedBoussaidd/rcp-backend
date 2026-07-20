package com.remipreparateur.performance.seance.repository;

import com.remipreparateur.performance.seance.entity.SeanceModeleDominante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SeanceModeleDominanteRepository
        extends JpaRepository<SeanceModeleDominante, SeanceModeleDominante.Pk> {

    List<SeanceModeleDominante> findBySeanceModeleId(UUID seanceModeleId);

    void deleteBySeanceModeleId(UUID seanceModeleId);
}
