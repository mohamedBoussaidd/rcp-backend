package com.remipreparateur.repository;

import com.remipreparateur.entity.Blessure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface BlessureRepository extends JpaRepository<Blessure, UUID> {
    List<Blessure> findByJoueurIdOrderByDateBlessureDesc(UUID joueurId);
    List<Blessure> findAllByOrderByDateBlessureDesc();
}
