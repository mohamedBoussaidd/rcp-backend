package com.remipreparateur.tactical.importphoto.repository;

import com.remipreparateur.tactical.importphoto.entity.ImportPhotoJournal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.UUID;

public interface ImportPhotoJournalRepository extends JpaRepository<ImportPhotoJournal, UUID> {

    /** Nombre d'appels du club depuis un instant (quota par jour). */
    long countByClubIdAndCreatedAtAfter(UUID clubId, LocalDateTime depuis);
}
