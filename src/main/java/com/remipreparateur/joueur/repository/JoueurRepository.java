package com.remipreparateur.joueur.repository;

import com.remipreparateur.joueur.entity.Joueur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface JoueurRepository extends JpaRepository<Joueur, UUID> {
    List<Joueur> findByStatut(String statut);
    List<Joueur> findByStatutNot(String statut);
    java.util.Optional<Joueur> findByPrenomIgnoreCase(String prenom);

    // ── Scoping par equipe ──
    List<Joueur> findByStatutNotAndEquipeIdIn(String statut, java.util.Collection<UUID> equipeIds);
    List<Joueur> findByEquipeIdIn(java.util.Collection<UUID> equipeIds);
    long countByEquipeIdIn(java.util.Collection<UUID> equipeIds);
}
