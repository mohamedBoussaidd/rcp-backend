package com.remipreparateur.performance.seance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Sous-principe du projet de jeu travaillé par un MODÈLE de séance (référentiel V61). */
@Entity
@Table(name = "seance_modele_sous_principe")
@IdClass(SeanceModeleSousPrincipe.Pk.class)
@Getter
@Setter
public class SeanceModeleSousPrincipe {

    @Id
    @Column(name = "seance_modele_id", nullable = false)
    private UUID seanceModeleId;

    @Id
    @Column(name = "sous_principe_id", nullable = false)
    private UUID sousPrincipeId;

    public static class Pk implements Serializable {
        private UUID seanceModeleId;
        private UUID sousPrincipeId;

        public Pk() {}
        public Pk(UUID seanceModeleId, UUID sousPrincipeId) { this.seanceModeleId = seanceModeleId; this.sousPrincipeId = sousPrincipeId; }

        @Override public boolean equals(Object o) {
            return o instanceof Pk pk && Objects.equals(seanceModeleId, pk.seanceModeleId) && Objects.equals(sousPrincipeId, pk.sousPrincipeId);
        }
        @Override public int hashCode() { return Objects.hash(seanceModeleId, sousPrincipeId); }
    }
}
