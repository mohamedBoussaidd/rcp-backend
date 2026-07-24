package com.remipreparateur.tactical.schema.repository;

import com.remipreparateur.tactical.schema.entity.SchemaTactique;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface SchemaTactiqueRepository extends JpaRepository<SchemaTactique, UUID> {
    List<SchemaTactique> findByClubIdOrderByUpdatedAtDesc(UUID clubId);

    /** Schémas FOURNIS (globaux, posés par le super-admin), visibles par tous les clubs. */
    List<SchemaTactique> findByClubIdIsNullOrderByUpdatedAtDesc();

    /** Tous les schémas appartenant à un club (recherche cross-club super-admin). */
    List<SchemaTactique> findByClubIdIsNotNullOrderByUpdatedAtDesc();

    /** Schémas des clubs sélectionnés (recherche cross-club filtrée). */
    List<SchemaTactique> findByClubIdInOrderByUpdatedAtDesc(java.util.Collection<UUID> clubIds);
}
