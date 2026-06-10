package com.remipreparateur.repository;

import com.remipreparateur.entity.SchemaTactique;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface SchemaTactiqueRepository extends JpaRepository<SchemaTactique, UUID> {
    List<SchemaTactique> findByClubIdOrderByUpdatedAtDesc(UUID clubId);
}
