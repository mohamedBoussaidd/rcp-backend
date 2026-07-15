package com.remipreparateur.medical.protocole.repository;

import com.remipreparateur.medical.protocole.entity.ProtocoleModeleEtape;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProtocoleModeleEtapeRepository extends JpaRepository<ProtocoleModeleEtape, UUID> {
    List<ProtocoleModeleEtape> findByModeleIdOrderByOrdreAsc(UUID modeleId);
    List<ProtocoleModeleEtape> findByModeleIdInOrderByOrdreAsc(List<UUID> modeleIds);
    void deleteByModeleId(UUID modeleId);
}
