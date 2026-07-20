package com.remipreparateur.performance.seance.repository;

import com.remipreparateur.performance.seance.entity.SeanceDominante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SeanceDominanteRepository extends JpaRepository<SeanceDominante, SeanceDominante.Pk> {

    List<SeanceDominante> findBySeanceId(UUID seanceId);

    void deleteBySeanceId(UUID seanceId);
}
