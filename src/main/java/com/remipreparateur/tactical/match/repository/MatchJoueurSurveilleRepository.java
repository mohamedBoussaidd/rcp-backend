package com.remipreparateur.tactical.match.repository;

import com.remipreparateur.tactical.match.entity.MatchJoueurSurveille;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface MatchJoueurSurveilleRepository extends JpaRepository<MatchJoueurSurveille, UUID> {
    List<MatchJoueurSurveille> findByMatchIdOrderByCreatedAtAsc(UUID matchId);
}
