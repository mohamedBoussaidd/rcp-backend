package com.remipreparateur.documentadmin.repository;

import com.remipreparateur.documentadmin.entity.TypeDocumentRequis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TypeDocumentRequisRepository extends JpaRepository<TypeDocumentRequis, UUID> {
    List<TypeDocumentRequis> findByClubIdOrderByOrdreAsc(UUID clubId);
    List<TypeDocumentRequis> findByClubIdAndActifTrueOrderByOrdreAsc(UUID clubId);
    Optional<TypeDocumentRequis> findByClubIdAndCodeIgnoreCase(UUID clubId, String code);
}
