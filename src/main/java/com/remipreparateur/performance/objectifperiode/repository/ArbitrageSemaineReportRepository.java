package com.remipreparateur.performance.objectifperiode.repository;

import com.remipreparateur.performance.objectifperiode.entity.ArbitrageSemaineReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ArbitrageSemaineReportRepository extends JpaRepository<ArbitrageSemaineReport, UUID> {

    List<ArbitrageSemaineReport> findByArbitrageId(UUID arbitrageId);

    void deleteByArbitrageId(UUID arbitrageId);
}
