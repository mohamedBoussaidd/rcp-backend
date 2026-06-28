package com.remipreparateur.tactical.diaporama.repository;

import com.remipreparateur.tactical.diaporama.entity.Slide;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface SlideRepository extends JpaRepository<Slide, UUID> {
    List<Slide> findByDiaporamaIdOrderByOrdreAsc(UUID diaporamaId);
    void deleteByDiaporamaId(UUID diaporamaId);
}
