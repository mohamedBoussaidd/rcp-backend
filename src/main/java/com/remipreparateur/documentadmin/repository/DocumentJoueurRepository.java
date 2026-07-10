package com.remipreparateur.documentadmin.repository;

import com.remipreparateur.documentadmin.entity.DocumentJoueur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentJoueurRepository extends JpaRepository<DocumentJoueur, UUID> {
    List<DocumentJoueur> findByJoueurIdIn(Collection<UUID> joueurIds);
    Optional<DocumentJoueur> findByJoueurIdAndTypeDocumentRequisId(UUID joueurId, UUID typeDocumentRequisId);
    List<DocumentJoueur> findByStatutAndDateExpirationBefore(String statut, LocalDate date);
}
