package com.remipreparateur.entretien.repository;

import com.remipreparateur.entretien.entity.Entretien;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EntretienRepository extends JpaRepository<Entretien, UUID> {

    List<Entretien> findByJoueurIdOrderByDateEntretienDescCreatedAtDesc(UUID joueurId);

    List<Entretien> findByJoueurIdAndVisibiliteOrderByDateEntretienDescCreatedAtDesc(UUID joueurId, String visibilite);

    List<Entretien> findByJoueurIdInOrderByDateEntretienDescCreatedAtDesc(List<UUID> joueurIds);
}
