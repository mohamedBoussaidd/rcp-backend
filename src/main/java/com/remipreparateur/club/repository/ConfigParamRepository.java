package com.remipreparateur.club.repository;

import com.remipreparateur.club.entity.ConfigParam;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfigParamRepository extends JpaRepository<ConfigParam, String> {
}
