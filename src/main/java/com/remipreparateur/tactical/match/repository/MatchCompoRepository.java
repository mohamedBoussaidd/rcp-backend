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

    /** Compos de plusieurs matchs d'un coup : le cumul de cartons balaye une saison entière. */
    List<MatchCompo> findByMatchIdIn(java.util.Collection<UUID> matchIds);

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

    /**
     * Compo + match d'un joueur, du plus récent au plus ancien, pour l'onglet Compétition.
     * On remonte le couple complet : le temps de jeu GPS se lit sur la séance liée au match,
     * qui n'est connue que par {@code MatchPrepa}.
     *
     * <p>{@code depuis} est obligatoire, et le garde {@code :depuis is null} a été retiré :
     * PostgreSQL refuse un paramètre dont il ne peut pas déduire le type (« could not determine
     * data type of parameter »), et un {@code ? is null} isolé ne lui donne aucun contexte. Les
     * deux appelants calculent de toute façon toujours une date de début.
     */
    @Query("select c as compo, m as match from MatchCompo c, MatchPrepa m " +
           "where c.matchId = m.id and c.joueurId = :joueurId and m.equipeId = :equipeId " +
           "and m.dateMatch >= :depuis " +
           "order by m.dateMatch desc nulls last")
    List<CompoMatchAgg> historiqueJoueur(UUID joueurId, UUID equipeId, java.time.LocalDate depuis);

    interface CompoMatchAgg {
        MatchCompo getCompo();
        com.remipreparateur.tactical.match.entity.MatchPrepa getMatch();
    }
}
