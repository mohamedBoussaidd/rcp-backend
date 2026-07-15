package com.remipreparateur.medical.protocole.repository;

import com.remipreparateur.medical.protocole.entity.ProtocoleModele;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProtocoleModeleRepository extends JpaRepository<ProtocoleModele, UUID> {
    List<ProtocoleModele> findByClubIdOrderByOrdreAscNomAsc(UUID clubId);
    List<ProtocoleModele> findByClubIdAndActifTrueOrderByOrdreAscNomAsc(UUID clubId);
    boolean existsByClubId(UUID clubId);
}
