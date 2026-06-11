package com.remipreparateur.tactical.seancetechnique.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "seance_technique_exercice")
@Getter
@Setter
public class SeanceTechniqueExercice {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "seance_technique_id", nullable = false)
    private UUID seanceTechniqueId;

    @Column(name = "exercice_id", nullable = false)
    private UUID exerciceId;

    @Column(name = "ordre", nullable = false)
    private Short ordre = 0;
}
