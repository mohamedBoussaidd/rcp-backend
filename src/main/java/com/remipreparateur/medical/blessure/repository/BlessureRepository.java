package com.remipreparateur.medical.blessure.repository;

import com.remipreparateur.medical.blessure.entity.Blessure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface BlessureRepository extends JpaRepository<Blessure, UUID> {
    List<Blessure> findByJoueurIdOrderByDateBlessureDesc(UUID joueurId);
    List<Blessure> findAllByOrderByDateBlessureDesc();
    List<Blessure> findByEquipeIdInOrderByDateBlessureDesc(java.util.Collection<UUID> equipeIds);

    /** Blessures encore en cours (sans retour effectif) pour une équipe. */
    List<Blessure> findByEquipeIdAndDateRetourEffectifIsNull(UUID equipeId);

    /** Blessures non soldées dont la date de retour prévue est atteinte / dépassée (réconciliation auto). */
    @Query("""
            SELECT b FROM Blessure b
            WHERE b.statut <> 'RETABLI'
              AND b.dateRetourPrevue IS NOT NULL
              AND b.dateRetourPrevue <= :jour
            """)
    List<Blessure> findRetoursDepasses(@Param("jour") LocalDate jour);
}
