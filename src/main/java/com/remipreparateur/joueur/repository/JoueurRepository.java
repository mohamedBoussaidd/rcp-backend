package com.remipreparateur.joueur.repository;

import com.remipreparateur.joueur.entity.Joueur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface JoueurRepository extends JpaRepository<Joueur, UUID> {
    List<Joueur> findByStatut(String statut);
    List<Joueur> findByStatutNot(String statut);
    java.util.Optional<Joueur> findByPrenomIgnoreCase(String prenom);

    // ── Scoping par équipe = appartenance à l'effectif de la saison EN_COURS (Phase 4) ──
    // Ces méthodes ne lisent PLUS le cache joueur.equipe_id (supprimé V51) : elles dérivent
    // l'appartenance depuis effectif_saison. Signatures conservées → aucun call-site à modifier.
    @Query("""
        SELECT DISTINCT j FROM Joueur j, EffectifSaison es, Saison s
        WHERE es.joueurId = j.id AND s.id = es.saisonId AND s.statut = 'EN_COURS'
          AND es.equipeId IN :equipeIds AND j.statut <> :statut
        """)
    List<Joueur> findByStatutNotAndEquipeIdIn(@Param("statut") String statut,
                                              @Param("equipeIds") java.util.Collection<UUID> equipeIds);

    @Query("""
        SELECT DISTINCT j FROM Joueur j, EffectifSaison es, Saison s
        WHERE es.joueurId = j.id AND s.id = es.saisonId AND s.statut = 'EN_COURS'
          AND es.equipeId IN :equipeIds
        """)
    List<Joueur> findByEquipeIdIn(@Param("equipeIds") java.util.Collection<UUID> equipeIds);

    @Query("""
        SELECT COUNT(DISTINCT j.id) FROM Joueur j, EffectifSaison es, Saison s
        WHERE es.joueurId = j.id AND s.id = es.saisonId AND s.statut = 'EN_COURS'
          AND es.equipeId IN :equipeIds
        """)
    long countByEquipeIdIn(@Param("equipeIds") java.util.Collection<UUID> equipeIds);

    // ── Scoping par club (fiches niveau club, y compris non assignées à une équipe) ──
    List<Joueur> findByClubId(UUID clubId);
    List<Joueur> findByStatutNotAndClubId(String statut, UUID clubId);

    // ── Qualité dérivée « fiche = joueur » (Phase 2) ──
    // Une fiche du club est un JOUEUR si elle est dans ≥ 1 effectif de saison (joueur assigné OU
    // joueur-entraîneur), OU si elle n'est rattachée à aucun compte de rôle staff (fiche « pool »
    // non encore assignée). Exclut donc les fiches purement staff (Phase 3). Corrélation par id.

    /** Fiches JOUEUR actives d'un club (exclut les fiches purement staff) — pour findAll(). */
    @Query("""
        SELECT j FROM Joueur j
        WHERE j.clubId = :club AND j.statut <> 'inactif'
          AND ( EXISTS (SELECT es.id FROM EffectifSaison es WHERE es.joueurId = j.id)
                OR NOT EXISTS (SELECT u.id FROM Utilisateur u
                               WHERE u.joueurId = j.id
                                 AND u.role <> com.remipreparateur.auth.entity.Role.JOUEUR) )
        """)
    List<Joueur> findJoueursActifsByClub(@Param("club") UUID club);

    /** Même dérivation, tous statuts (inactifs inclus) — pour findAllPlayers(). */
    @Query("""
        SELECT j FROM Joueur j
        WHERE j.clubId = :club
          AND ( EXISTS (SELECT es.id FROM EffectifSaison es WHERE es.joueurId = j.id)
                OR NOT EXISTS (SELECT u.id FROM Utilisateur u
                               WHERE u.joueurId = j.id
                                 AND u.role <> com.remipreparateur.auth.entity.Role.JOUEUR) )
        """)
    List<Joueur> findJoueursByClub(@Param("club") UUID club);
}
