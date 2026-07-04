package com.remipreparateur.entretien.repository;

import com.remipreparateur.entretien.entity.EntretienAxe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EntretienAxeRepository extends JpaRepository<EntretienAxe, UUID> {

    List<EntretienAxe> findByEntretienId(UUID entretienId);

    List<EntretienAxe> findByEntretienIdIn(List<UUID> entretienIds);

    List<EntretienAxe> findByAxeTravailId(UUID axeTravailId);

    boolean existsByAxeTravailId(UUID axeTravailId);

    void deleteByEntretienId(UUID entretienId);
}
