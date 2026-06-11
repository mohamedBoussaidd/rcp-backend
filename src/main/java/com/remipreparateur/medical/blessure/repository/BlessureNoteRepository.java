package com.remipreparateur.medical.blessure.repository;

import com.remipreparateur.medical.blessure.entity.BlessureNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface BlessureNoteRepository extends JpaRepository<BlessureNote, UUID> {
    List<BlessureNote> findByBlessureIdOrderByDateDescCreatedAtDesc(UUID blessureId);
}
