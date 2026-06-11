package com.remipreparateur.tactical.match.repository;

import com.remipreparateur.tactical.match.entity.MatchCompo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface MatchCompoRepository extends JpaRepository<MatchCompo, UUID> {
    List<MatchCompo> findByMatchId(UUID matchId);
    void deleteByMatchId(UUID matchId);

    /** Nombre d'apparitions par joueur et par statut, sur tous les matchs d'une équipe. */
    @Query("select c.joueurId as joueurId, c.statut as statut, count(c) as nb " +
           "from MatchCompo c, MatchPrepa m " +
           "where c.matchId = m.id and m.equipeId = :equipeId " +
           "group by c.joueurId, c.statut")
    List<CompoStatAgg> aggregerStatuts(UUID equipeId);

    interface CompoStatAgg {
        UUID getJoueurId();
        String getStatut();
        long getNb();
    }
}
