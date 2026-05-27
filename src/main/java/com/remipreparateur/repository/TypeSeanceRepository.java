package com.remipreparateur.repository;

import com.remipreparateur.entity.TypeSeance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TypeSeanceRepository extends JpaRepository<TypeSeance, UUID> {
    Optional<TypeSeance> findByCode(String code);
}
