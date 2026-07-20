package com.remipreparateur.performance.seance.repository;

import com.remipreparateur.performance.seance.entity.SeanceModeleSousPrincipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SeanceModeleSousPrincipeRepository
        extends JpaRepository<SeanceModeleSousPrincipe, SeanceModeleSousPrincipe.Pk> {

    List<SeanceModeleSousPrincipe> findBySeanceModeleId(UUID seanceModeleId);

    void deleteBySeanceModeleId(UUID seanceModeleId);
}
