package com.remipreparateur.repository;

import com.remipreparateur.entity.Seance;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SeanceRepository extends JpaRepository<Seance, UUID> {

    @EntityGraph(attributePaths = "typeSeance")
    List<Seance> findAll();

    @EntityGraph(attributePaths = "typeSeance")
    List<Seance> findByDateBetweenOrderByDateAscHeureDebutAsc(LocalDate debut, LocalDate fin);

    Optional<Seance> findByDateAndTypeSeanceId(LocalDate date, UUID typeSeanceId);
}
