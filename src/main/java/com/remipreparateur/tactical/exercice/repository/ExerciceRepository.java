package com.remipreparateur.tactical.exercice.repository;

import com.remipreparateur.tactical.exercice.entity.Exercice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ExerciceRepository extends JpaRepository<Exercice, UUID> {
    List<Exercice> findByClubIdOrderByCreatedAtDesc(UUID clubId);

    /** Exercices GLOBAUX (club_id NULL, bibliothèque super-admin) — visibles par tous les clubs. */
    List<Exercice> findByClubIdIsNullOrderByCreatedAtDesc();

    /** Tous les exercices appartenant à un club (recherche cross-club super-admin). */
    List<Exercice> findByClubIdIsNotNullOrderByCreatedAtDesc();

    /** Exercices des clubs sélectionnés (recherche cross-club filtrée). */
    List<Exercice> findByClubIdInOrderByCreatedAtDesc(java.util.Collection<UUID> clubIds);
}
