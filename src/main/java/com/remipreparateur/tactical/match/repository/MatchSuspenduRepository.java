package com.remipreparateur.tactical.match.repository;

import com.remipreparateur.tactical.match.entity.MatchSuspendu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface MatchSuspenduRepository extends JpaRepository<MatchSuspendu, UUID> {
    List<MatchSuspendu> findByMatchId(UUID matchId);
    void deleteByMatchId(UUID matchId);
}
