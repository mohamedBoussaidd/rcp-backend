package com.remipreparateur.ia.repository;

import com.remipreparateur.ia.entity.IaFournisseur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IaFournisseurRepository extends JpaRepository<IaFournisseur, String> {

    List<IaFournisseur> findAllByOrderByLibelleAsc();
}
