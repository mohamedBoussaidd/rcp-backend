package com.remipreparateur.tactical.diaporama.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Slide d'un {@link Diaporama}, ordonné et réordonnable. Un seul des champs de contenu est
 * renseigné selon le {@code type} :
 * <ul>
 *   <li>{@code SCHEMA}     → {@code schemaJson} (+ {@code apercu}), snapshot du schéma tactique ;</li>
 *   <li>{@code IMAGE}      → {@code imageSrc} (URL externe ou data URL d'upload) ;</li>
 *   <li>{@code VIDEO_LIEN} → {@code videoUrl} (lien YouTube / Vimeo).</li>
 * </ul>
 */
@Entity
@Table(name = "slide")
@Getter
@Setter
public class Slide {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "diaporama_id", nullable = false)
    private UUID diaporamaId;

    @Column(name = "ordre", nullable = false)
    private int ordre;

    /** {@code SCHEMA} | {@code IMAGE} | {@code VIDEO_LIEN}. */
    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "titre")
    private String titre;

    @Column(name = "schema_json", columnDefinition = "text")
    private String schemaJson;

    @Column(name = "apercu", columnDefinition = "text")
    private String apercu;

    @Column(name = "image_src", columnDefinition = "text")
    private String imageSrc;

    @Column(name = "video_url")
    private String videoUrl;

    @Column(name = "texte", columnDefinition = "text")
    private String texte;

    /** Mise en forme du slide TEXTE (JSON libre côté front : couleurs, taille, alignements). */
    @Column(name = "style_json", columnDefinition = "text")
    private String styleJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
