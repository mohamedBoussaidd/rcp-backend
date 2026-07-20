package com.remipreparateur.performance.seance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Sous-principe du projet de jeu travaillé dans une séance (référentiel seedé). */
@Entity
@Table(name = "seance_sous_principe")
@IdClass(SeanceSousPrincipe.Pk.class)
@Getter
@Setter
public class SeanceSousPrincipe {

    @Id
    @Column(name = "seance_id", nullable = false)
    private UUID seanceId;

    @Id
    @Column(name = "sous_principe_id", nullable = false)
    private UUID sousPrincipeId;

    public static class Pk implements Serializable {
        private UUID seanceId;
        private UUID sousPrincipeId;

        public Pk() {}
        public Pk(UUID seanceId, UUID sousPrincipeId) { this.seanceId = seanceId; this.sousPrincipeId = sousPrincipeId; }

        @Override public boolean equals(Object o) {
            return o instanceof Pk pk && Objects.equals(seanceId, pk.seanceId) && Objects.equals(sousPrincipeId, pk.sousPrincipeId);
        }
        @Override public int hashCode() { return Objects.hash(seanceId, sousPrincipeId); }
    }
}
