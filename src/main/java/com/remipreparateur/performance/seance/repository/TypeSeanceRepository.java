package com.remipreparateur.performance.seance.repository;

import com.remipreparateur.performance.seance.entity.TypeSeance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TypeSeanceRepository extends JpaRepository<TypeSeance, UUID> {
    Optional<TypeSeance> findByCode(String code);
}
