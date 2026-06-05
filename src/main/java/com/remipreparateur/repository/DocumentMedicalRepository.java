package com.remipreparateur.repository;

import com.remipreparateur.entity.DocumentMedical;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentMedicalRepository extends JpaRepository<DocumentMedical, UUID> {
    List<DocumentMedical> findByJoueurIdOrderByDateDepotDesc(UUID joueurId);
    List<DocumentMedical> findAllByOrderByDateDepotDesc();
    List<DocumentMedical> findByEquipeIdInOrderByDateDepotDesc(Collection<UUID> equipeIds);
}
