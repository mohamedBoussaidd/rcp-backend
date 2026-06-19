package com.remipreparateur.auth.rbac;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleApplicatifRepository extends JpaRepository<RoleApplicatif, UUID> {

    /** Rôles système (communs à tous les clubs). */
    List<RoleApplicatif> findBySystemeTrue();

    /** Rôle système par code (ex. PREPARATEUR) — pour synchroniser le rôle « principal » d'un membre. */
    Optional<RoleApplicatif> findBySystemeTrueAndCode(String code);

    /** Rôles custom d'un club. */
    List<RoleApplicatif> findByClubId(UUID clubId);
}
