package com.remipreparateur.repository;

import com.remipreparateur.entity.DonneeGps;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface DonneeGpsRepository extends JpaRepository<DonneeGps, UUID> {
    List<DonneeGps> findBySeanceId(UUID seanceId);
    void deleteBySeanceId(UUID seanceId);
    List<DonneeGps> findByJoueurId(UUID joueurId);
    List<DonneeGps> findByJoueurIdOrderBySeanceDateDesc(UUID joueurId);
    java.util.Optional<DonneeGps> findByJoueurIdAndSeanceId(UUID joueurId, UUID seanceId);
}
