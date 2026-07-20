package com.remipreparateur.auth.preference;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PreferenceUtilisateurRepository
        extends JpaRepository<PreferenceUtilisateur, PreferenceUtilisateur.Pk> {

    List<PreferenceUtilisateur> findByUtilisateurId(UUID utilisateurId);

    Optional<PreferenceUtilisateur> findByUtilisateurIdAndCle(UUID utilisateurId, String cle);
}
