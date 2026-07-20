package com.remipreparateur.tactical.importphoto.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Une ligne par appel d'import photo : quota (comptage club/jour), audit de coût,
 * et conservation de la photo d'origine (photo_path sur ./data/import-photos).
 */
@Entity
@Table(name = "import_photo_journal")
@Getter
@Setter
public class ImportPhotoJournal {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(name = "utilisateur_id")
    private UUID utilisateurId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** OK | ERREUR | ILLISIBLE */
    @Column(name = "statut", nullable = false, length = 20)
    private String statut = "OK";

    @Column(name = "photo_path")
    private String photoPath;

    @Column(name = "message", length = 500)
    private String message;
}
