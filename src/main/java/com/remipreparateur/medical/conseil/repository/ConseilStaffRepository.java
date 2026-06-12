package com.remipreparateur.medical.conseil.repository;

import com.remipreparateur.medical.conseil.entity.ConseilStaff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ConseilStaffRepository extends JpaRepository<ConseilStaff, UUID> {

    /** Conseils d'équipe (joueur_id null) d'une équipe donnée. */
    List<ConseilStaff> findByEquipeIdAndJoueurIdIsNullOrderByCreatedAtDesc(UUID equipeId);

    /** Conseils personnels d'un joueur. */
    List<ConseilStaff> findByJoueurIdOrderByCreatedAtDesc(UUID joueurId);

    /** Conseils d'équipe + conseils personnels du joueur (vue joueur). */
    List<ConseilStaff> findByEquipeIdAndJoueurIdIsNullOrJoueurIdOrderByCreatedAtDesc(UUID equipeId, UUID joueurId);
}
