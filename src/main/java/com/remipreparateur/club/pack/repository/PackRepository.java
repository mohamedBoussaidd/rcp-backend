package com.remipreparateur.club.pack.repository;

import com.remipreparateur.club.pack.entity.Pack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PackRepository extends JpaRepository<Pack, String> {

    List<Pack> findAllByOrderByOrdreAsc();
}
