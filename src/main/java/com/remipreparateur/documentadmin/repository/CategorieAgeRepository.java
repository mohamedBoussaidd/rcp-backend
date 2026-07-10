package com.remipreparateur.documentadmin.repository;

import com.remipreparateur.documentadmin.entity.CategorieAge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategorieAgeRepository extends JpaRepository<CategorieAge, UUID> {
    List<CategorieAge> findByClubIdOrderByOrdreAsc(UUID clubId);
    List<CategorieAge> findByClubIdAndActifTrueOrderByOrdreAsc(UUID clubId);
    Optional<CategorieAge> findByClubIdAndCodeIgnoreCase(UUID clubId, String code);
}
