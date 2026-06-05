package com.remipreparateur.repository;

import com.remipreparateur.entity.DonneeGps;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface DonneeGpsRepository extends JpaRepository<DonneeGps, UUID> {
    List<DonneeGps> findBySeanceId(UUID seanceId);
    void deleteBySeanceId(UUID seanceId);
    List<DonneeGps> findByJoueurId(UUID joueurId);
    List<DonneeGps> findByJoueurIdOrderBySeanceDateDesc(UUID joueurId);
    java.util.Optional<DonneeGps> findByJoueurIdAndSeanceId(UUID joueurId, UUID seanceId);

    /** Fiche vitesse par joueur (record + moyenne des vitesses max GPS), limitée aux équipes données. */
    @Query("select g.joueur.id as joueurId, max(g.vitesseMaxKmh) as vmax, avg(g.vitesseMaxKmh) as vmoy " +
           "from DonneeGps g where g.vitesseMaxKmh is not null and g.joueur.equipeId in :equipeIds " +
           "group by g.joueur.id")
    List<VitesseAgg> aggregerVitesses(java.util.Collection<UUID> equipeIds);

    /** Variante sans filtre d'équipe (super-admin uniquement). */
    @Query("select g.joueur.id as joueurId, max(g.vitesseMaxKmh) as vmax, avg(g.vitesseMaxKmh) as vmoy " +
           "from DonneeGps g where g.vitesseMaxKmh is not null group by g.joueur.id")
    List<VitesseAgg> aggregerToutesVitesses();

    interface VitesseAgg {
        UUID getJoueurId();
        BigDecimal getVmax();
        Double getVmoy();
    }
}
