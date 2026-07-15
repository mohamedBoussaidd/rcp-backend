package com.remipreparateur.contrat.repository;

import com.remipreparateur.contrat.entity.BulletinPaie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BulletinPaieRepository extends JpaRepository<BulletinPaie, UUID> {
    List<BulletinPaie> findByClubIdAndPeriodeOrderByDeposeLeDesc(UUID clubId, LocalDate periode);
    Optional<BulletinPaie> findByClubIdAndJoueurIdAndPeriode(UUID clubId, UUID joueurId, LocalDate periode);
    /** Bulletins visibles par la personne : uniquement ceux déjà distribués. */
    List<BulletinPaie> findByJoueurIdAndNotifieLeIsNotNullOrderByPeriodeDesc(UUID joueurId);
    List<BulletinPaie> findByClubIdAndPeriodeAndNotifieLeIsNull(UUID clubId, LocalDate periode);

    @Query("select distinct b.periode from BulletinPaie b where b.clubId = :clubId order by b.periode desc")
    List<LocalDate> periodes(UUID clubId);
}
