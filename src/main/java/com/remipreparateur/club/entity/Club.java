package com.remipreparateur.club.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "club")
@Getter
@Setter
public class Club {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "nom", nullable = false)
    private String nom;

    @Column(name = "logo")
    private String logo;

    @Column(name = "date_creation", nullable = false)
    private LocalDate dateCreation = LocalDate.now();

    @Column(name = "president_id")
    private UUID presidentId;

    /** Club actif (par défaut) ou archivé par le super-admin. */
    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    /** Pack commercial du club (bundle de modules). {@code null} = aucun pack (fail-open : tout actif). */
    @Column(name = "pack_code")
    private String packCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
