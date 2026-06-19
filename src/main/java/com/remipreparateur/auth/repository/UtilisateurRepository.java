package com.remipreparateur.auth.repository;

import com.remipreparateur.auth.entity.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UtilisateurRepository extends JpaRepository<Utilisateur, UUID> {
    Optional<Utilisateur> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<Utilisateur> findByClubId(UUID clubId);
    List<Utilisateur> findByEquipeId(UUID equipeId);
    List<Utilisateur> findByEquipeIdAndRoleIn(UUID equipeId, List<com.remipreparateur.auth.entity.Role> roles);
    Optional<Utilisateur> findByJoueurId(UUID joueurId);
    /** Une fiche joueur ne peut être reliée qu'à un seul compte. */
    boolean existsByJoueurId(UUID joueurId);
    boolean existsByJoueurIdAndIdNot(UUID joueurId, UUID id);
}
