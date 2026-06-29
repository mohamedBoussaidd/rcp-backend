package com.remipreparateur.saison.repository;

import com.remipreparateur.saison.entity.Saison;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SaisonRepository extends JpaRepository<Saison, UUID> {

    List<Saison> findByClubIdInOrderByDateDebutDesc(Collection<UUID> clubIds);

    List<Saison> findByClubIdOrderByDateDebutDesc(UUID clubId);

    Optional<Saison> findFirstByClubIdAndStatut(UUID clubId, String statut);

    /** Saison précédente (la plus récente hors celle-ci) pour proposer la reconduction. */
    Optional<Saison> findFirstByClubIdAndIdNotOrderByDateFinDesc(UUID clubId, UUID exclu);
}
