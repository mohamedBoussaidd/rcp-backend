package com.remipreparateur.medical.blessure.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Note datée du journal d'évolution d'une blessure (soin, observation). */
@Entity
@Table(name = "blessure_note")
@Getter
@Setter
public class BlessureNote {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "blessure_id", nullable = false)
    private UUID blessureId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "texte", nullable = false)
    private String texte;

    @Column(name = "depose_par")
    private UUID deposePar;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
