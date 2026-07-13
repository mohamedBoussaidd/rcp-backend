package com.remipreparateur.performance.importation.repository;

import com.remipreparateur.performance.importation.entity.AliasJoueurImport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AliasJoueurImportRepository extends JpaRepository<AliasJoueurImport, UUID> {

    List<AliasJoueurImport> findByClubId(UUID clubId);

    Optional<AliasJoueurImport> findByClubIdAndAlias(UUID clubId, String alias);
}
