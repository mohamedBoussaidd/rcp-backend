package com.remipreparateur.badge.repository;

import com.remipreparateur.badge.entity.BadgeDefinition;
import com.remipreparateur.badge.entity.BadgePortee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BadgeDefinitionRepository extends JpaRepository<BadgeDefinition, UUID> {

    List<BadgeDefinition> findAllByActifTrueOrderByOrdreAsc();

    List<BadgeDefinition> findAllByOrderByOrdreAsc();

    List<BadgeDefinition> findAllByPorteeOrderByOrdreAsc(BadgePortee portee);

    Optional<BadgeDefinition> findByCle(String cle);
}
