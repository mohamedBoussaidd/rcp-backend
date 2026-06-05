package com.remipreparateur.repository;

import com.remipreparateur.entity.RpeSeance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RpeSeanceRepository extends JpaRepository<RpeSeance, UUID> {
    List<RpeSeance> findByJoueurIdOrderByDateDesc(UUID joueurId);
    Optional<RpeSeance> findByJoueurIdAndSeanceId(UUID joueurId, UUID seanceId);
    List<RpeSeance> findAllByOrderByDateDesc();
    List<RpeSeance> findByEquipeIdInOrderByDateDesc(Collection<UUID> equipeIds);
}
