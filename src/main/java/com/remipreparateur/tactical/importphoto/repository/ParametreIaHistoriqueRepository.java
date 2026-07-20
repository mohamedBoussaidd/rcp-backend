package com.remipreparateur.tactical.importphoto.repository;

import com.remipreparateur.tactical.importphoto.entity.ParametreIaHistorique;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ParametreIaHistoriqueRepository extends JpaRepository<ParametreIaHistorique, UUID> {

    List<ParametreIaHistorique> findTop20ByCleOrderByCreatedAtDesc(String cle);
}
