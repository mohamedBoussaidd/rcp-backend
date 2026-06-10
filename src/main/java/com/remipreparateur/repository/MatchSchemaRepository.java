package com.remipreparateur.repository;

import com.remipreparateur.entity.MatchSchema;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface MatchSchemaRepository extends JpaRepository<MatchSchema, UUID> {
    List<MatchSchema> findByMatchIdOrderByOrdreAsc(UUID matchId);
}
