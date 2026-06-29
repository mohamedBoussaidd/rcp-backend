package com.remipreparateur.saison.repository;

import com.remipreparateur.saison.entity.EffectifSaison;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EffectifSaisonRepository extends JpaRepository<EffectifSaison, UUID> {

    List<EffectifSaison> findBySaisonIdAndEquipeId(UUID saisonId, UUID equipeId);

    void deleteBySaisonIdAndEquipeId(UUID saisonId, UUID equipeId);

    void deleteBySaisonId(UUID saisonId);

    boolean existsBySaisonIdAndEquipeIdAndJoueurId(UUID saisonId, UUID equipeId, UUID joueurId);

    int countBySaisonIdAndEquipeId(UUID saisonId, UUID equipeId);
}
