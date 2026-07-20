package com.remipreparateur.performance.seance.repository;

import com.remipreparateur.performance.seance.entity.ReferentielRoleBloc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReferentielRoleBlocRepository extends JpaRepository<ReferentielRoleBloc, String> {
    List<ReferentielRoleBloc> findAllByOrderByOrdreAsc();
}
