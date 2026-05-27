package com.remipreparateur.repository;

import com.remipreparateur.entity.Joueur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface JoueurRepository extends JpaRepository<Joueur, UUID> {
    List<Joueur> findByStatut(String statut);
    java.util.Optional<Joueur> findByPrenomIgnoreCase(String prenom);
}
