package com.remipreparateur.tactical.regles.repository;

import com.remipreparateur.tactical.regles.entity.RegleTactique;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.List;

@Repository
public interface RegleTactiqueRepository extends JpaRepository<RegleTactique, UUID> {

    List<RegleTactique> findByEquipeIdOrderByUpdatedAtDesc(UUID equipeId);

    boolean existsByEquipeIdAndTypeAndSysteme(UUID equipeId, String type, String systeme);

    boolean existsByEquipeIdAndTypeAndSystemeAndIdNot(UUID equipeId, String type, String systeme, UUID id);
}
