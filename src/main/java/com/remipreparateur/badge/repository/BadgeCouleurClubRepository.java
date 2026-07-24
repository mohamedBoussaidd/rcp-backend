package com.remipreparateur.badge.repository;

import com.remipreparateur.badge.entity.BadgeCouleurClub;
import com.remipreparateur.badge.entity.BadgeCouleurClubId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BadgeCouleurClubRepository extends JpaRepository<BadgeCouleurClub, BadgeCouleurClubId> {

    List<BadgeCouleurClub> findAllByClubId(UUID clubId);
}
