package com.remipreparateur.tactical.importphoto.repository;

import com.remipreparateur.tactical.importphoto.entity.ClubParametre;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClubParametreRepository extends JpaRepository<ClubParametre, ClubParametre.Pk> {

    Optional<ClubParametre> findByClubIdAndCle(UUID clubId, String cle);

    List<ClubParametre> findByCle(String cle);
}
