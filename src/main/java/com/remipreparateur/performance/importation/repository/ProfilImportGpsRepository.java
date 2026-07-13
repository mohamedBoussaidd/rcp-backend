package com.remipreparateur.performance.importation.repository;

import com.remipreparateur.performance.importation.entity.ProfilImportGps;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfilImportGpsRepository extends JpaRepository<ProfilImportGps, UUID> {

    List<ProfilImportGps> findByClubId(UUID clubId);

    /** Profils globaux « fournisseur » (McLloyd…), proposés à tous les clubs. */
    List<ProfilImportGps> findByClubIdIsNull();

    /** Reconnaissance automatique d'un fichier déjà mappé par CE club. */
    Optional<ProfilImportGps> findFirstByClubIdAndSignatureEntetes(UUID clubId, String signature);

    /** Reconnaissance via un profil global si le club n'a pas le sien. */
    Optional<ProfilImportGps> findFirstByClubIdIsNullAndSignatureEntetes(String signature);
}
