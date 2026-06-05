package com.remipreparateur.repository;

import com.remipreparateur.entity.RtpEtape;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface RtpEtapeRepository extends JpaRepository<RtpEtape, UUID> {
    List<RtpEtape> findByBlessureIdOrderByOrdreAsc(UUID blessureId);
    boolean existsByBlessureId(UUID blessureId);
    void deleteByBlessureId(UUID blessureId);
}
