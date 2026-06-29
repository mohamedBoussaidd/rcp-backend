package com.remipreparateur.performance.seance.repository;

import com.remipreparateur.performance.seance.entity.ModeleSemaine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ModeleSemaineRepository extends JpaRepository<ModeleSemaine, UUID> {

    List<ModeleSemaine> findByEquipeIdInOrderByNomAsc(Collection<UUID> equipeIds);

    List<ModeleSemaine> findByEquipeIdOrderByNomAsc(UUID equipeId);
}
