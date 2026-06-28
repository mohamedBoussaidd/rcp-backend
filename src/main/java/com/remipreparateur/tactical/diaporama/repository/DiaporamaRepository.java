package com.remipreparateur.tactical.diaporama.repository;

import com.remipreparateur.tactical.diaporama.entity.Diaporama;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface DiaporamaRepository extends JpaRepository<Diaporama, UUID> {
    List<Diaporama> findByClubIdOrderByUpdatedAtDesc(UUID clubId);
}
