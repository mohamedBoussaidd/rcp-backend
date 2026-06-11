package com.remipreparateur.performance.poids.repository;

import com.remipreparateur.performance.poids.entity.HistoriquePoids;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HistoriquePoidsRepository extends JpaRepository<HistoriquePoids, Long> {

    List<HistoriquePoids> findByJoueurIdOrderByDateDesc(UUID joueurId);

    Optional<HistoriquePoids> findFirstByJoueurIdAndDateLessThanEqualOrderByDateDesc(UUID joueurId, LocalDate date);
}
