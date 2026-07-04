package com.remipreparateur.entretien.repository;

import com.remipreparateur.entretien.entity.AxeTravail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AxeTravailRepository extends JpaRepository<AxeTravail, UUID> {

    List<AxeTravail> findByJoueurIdOrderByCreatedAtDesc(UUID joueurId);

    List<AxeTravail> findByJoueurIdAndStatutOrderByCreatedAtDesc(UUID joueurId, String statut);
}
