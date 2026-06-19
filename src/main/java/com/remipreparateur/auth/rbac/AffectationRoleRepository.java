package com.remipreparateur.auth.rbac;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AffectationRoleRepository extends JpaRepository<AffectationRole, UUID> {

    List<AffectationRole> findByUserId(UUID userId);

    List<AffectationRole> findByUserIdAndClubId(UUID userId, UUID clubId);

    /** DELETE bulk immédiat (évite l'ordre Hibernate insert-avant-delete sur uq_affectation). */
    @Modifying
    @Query("delete from AffectationRole a where a.userId = :userId and a.clubId = :clubId")
    void deleteByUserIdAndClubId(@Param("userId") UUID userId, @Param("clubId") UUID clubId);

    long countByRoleId(UUID roleId);
}
