package com.remipreparateur.performance.seance.repository;

import com.remipreparateur.performance.seance.entity.SeanceSousPrincipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SeanceSousPrincipeRepository extends JpaRepository<SeanceSousPrincipe, SeanceSousPrincipe.Pk> {

    List<SeanceSousPrincipe> findBySeanceId(UUID seanceId);

    void deleteBySeanceId(UUID seanceId);
}
