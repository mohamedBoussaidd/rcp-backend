package com.remipreparateur.badge.repository;

import com.remipreparateur.badge.entity.BadgePaletteTon;
import com.remipreparateur.badge.entity.BadgeTon;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BadgePaletteTonRepository extends JpaRepository<BadgePaletteTon, BadgeTon> {
}
