package com.remipreparateur.joueur.repository;

import com.remipreparateur.joueur.entity.ObjectifJoueur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ObjectifJoueurRepository extends JpaRepository<ObjectifJoueur, UUID> {

    /** Les objectifs en cours d'abord (ATTEINT/ABANDONNE après), puis par échéance la plus proche. */
    List<ObjectifJoueur> findByJoueurIdOrderByStatutAscEcheanceAsc(UUID joueurId);
}
