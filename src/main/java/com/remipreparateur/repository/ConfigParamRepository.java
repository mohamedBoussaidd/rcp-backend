package com.remipreparateur.repository;

import com.remipreparateur.entity.ConfigParam;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfigParamRepository extends JpaRepository<ConfigParam, String> {
}
