package com.remipreparateur.repository;

import com.remipreparateur.entity.Equipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface EquipeRepository extends JpaRepository<Equipe, UUID> {
    List<Equipe> findByClubId(UUID clubId);
    long countByClubId(UUID clubId);
}
