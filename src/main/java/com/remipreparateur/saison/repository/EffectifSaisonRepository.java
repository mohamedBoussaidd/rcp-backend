package com.remipreparateur.saison.repository;

import com.remipreparateur.saison.entity.EffectifSaison;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EffectifSaisonRepository extends JpaRepository<EffectifSaison, UUID> {

    // ── Dérivation « équipe(s) courante(s) d'une personne » (Phase 4, remplace joueur.equipe_id) ──
    // Équipes de l'effectif de la saison EN_COURS du joueur, la plus récemment rejointe d'abord.
    @Query("""
        SELECT es.equipeId FROM EffectifSaison es, Saison s
        WHERE s.id = es.saisonId AND s.statut = 'EN_COURS' AND es.joueurId = :joueurId
        ORDER BY es.dateEntree DESC NULLS LAST
        """)
    List<UUID> findEquipesActivesOrdonnees(@Param("joueurId") UUID joueurId);

    // Repli hors-saison : équipes de TOUTES les saisons, la plus récente d'abord (pour tamponner
    // une donnée même quand aucune saison n'est EN_COURS).
    @Query("""
        SELECT es.equipeId FROM EffectifSaison es
        WHERE es.joueurId = :joueurId
        ORDER BY es.dateEntree DESC NULLS LAST
        """)
    List<UUID> findEquipesOrdonnees(@Param("joueurId") UUID joueurId);

    List<EffectifSaison> findBySaisonIdAndEquipeId(UUID saisonId, UUID equipeId);

    List<EffectifSaison> findBySaisonId(UUID saisonId);

    List<EffectifSaison> findByJoueurId(UUID joueurId);

    void deleteBySaisonIdAndEquipeId(UUID saisonId, UUID equipeId);

    void deleteBySaisonId(UUID saisonId);

    void deleteBySaisonIdAndEquipeIdAndJoueurId(UUID saisonId, UUID equipeId, UUID joueurId);

    boolean existsBySaisonIdAndEquipeIdAndJoueurId(UUID saisonId, UUID equipeId, UUID joueurId);

    boolean existsBySaisonIdAndJoueurId(UUID saisonId, UUID joueurId);

    boolean existsByJoueurId(UUID joueurId);

    int countBySaisonIdAndEquipeId(UUID saisonId, UUID equipeId);
}
