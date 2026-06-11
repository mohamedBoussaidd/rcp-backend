package com.remipreparateur.performance.seance.repository;

import com.remipreparateur.performance.seance.entity.TypeSeanceCible;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TypeSeanceCibleRepository extends JpaRepository<TypeSeanceCible, UUID> {

    List<TypeSeanceCible> findByClubId(UUID clubId);

    Optional<TypeSeanceCible> findByClubIdAndTypeSeanceId(UUID clubId, UUID typeSeanceId);
}
