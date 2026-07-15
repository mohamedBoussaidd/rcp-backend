package com.remipreparateur.contrat.repository;

import com.remipreparateur.contrat.entity.Contrat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ContratRepository extends JpaRepository<Contrat, UUID> {
    List<Contrat> findByClubIdOrderByDateDebutDesc(UUID clubId);
    List<Contrat> findByJoueurIdOrderByDateDebutDesc(UUID joueurId);
    /** Échéances exactes (scheduler J-90 / J-30). */
    List<Contrat> findByDateFin(LocalDate dateFin);
}
