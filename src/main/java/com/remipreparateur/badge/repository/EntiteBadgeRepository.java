package com.remipreparateur.badge.repository;

import com.remipreparateur.badge.entity.EntiteBadge;
import com.remipreparateur.badge.entity.TypeEntite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface EntiteBadgeRepository extends JpaRepository<EntiteBadge, UUID> {

    List<EntiteBadge> findAllByTypeEntiteAndEntiteId(TypeEntite typeEntite, UUID entiteId);

    List<EntiteBadge> findAllByTypeEntite(TypeEntite typeEntite);

    @Transactional
    void deleteByTypeEntiteAndEntiteId(TypeEntite typeEntite, UUID entiteId);

    @Transactional
    void deleteByBadgeId(UUID badgeId);
}
