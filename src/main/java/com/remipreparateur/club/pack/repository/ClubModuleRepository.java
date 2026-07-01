package com.remipreparateur.club.pack.repository;

import com.remipreparateur.club.pack.entity.ClubModule;
import com.remipreparateur.club.pack.entity.ClubModuleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClubModuleRepository extends JpaRepository<ClubModule, ClubModuleId> {

    List<ClubModule> findByClubId(UUID clubId);
}
