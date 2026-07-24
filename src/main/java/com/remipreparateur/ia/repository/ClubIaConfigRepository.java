package com.remipreparateur.ia.repository;

import com.remipreparateur.ia.entity.ClubIaConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ClubIaConfigRepository extends JpaRepository<ClubIaConfig, UUID> {
}
