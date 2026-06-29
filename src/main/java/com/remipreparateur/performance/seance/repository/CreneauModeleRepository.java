package com.remipreparateur.performance.seance.repository;

import com.remipreparateur.performance.seance.entity.CreneauModele;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface CreneauModeleRepository extends JpaRepository<CreneauModele, UUID> {

    @EntityGraph(attributePaths = "typeSeance")
    List<CreneauModele> findByModeleIdOrderByJourSemaineAscHeureDebutAscOrdreAsc(UUID modeleId);

    void deleteByModeleId(UUID modeleId);
}
