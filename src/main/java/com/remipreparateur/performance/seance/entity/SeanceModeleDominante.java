package com.remipreparateur.performance.seance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Dominante sélectionnée sur un MODÈLE de séance (même référentiel que {@link SeanceDominante}). */
@Entity
@Table(name = "seance_modele_dominante")
@IdClass(SeanceModeleDominante.Pk.class)
@Getter
@Setter
public class SeanceModeleDominante {

    @Id
    @Column(name = "seance_modele_id", nullable = false)
    private UUID seanceModeleId;

    @Id
    @Column(name = "dominante_id", nullable = false)
    private UUID dominanteId;

    public static class Pk implements Serializable {
        private UUID seanceModeleId;
        private UUID dominanteId;

        public Pk() {}
        public Pk(UUID seanceModeleId, UUID dominanteId) { this.seanceModeleId = seanceModeleId; this.dominanteId = dominanteId; }

        @Override public boolean equals(Object o) {
            return o instanceof Pk pk && Objects.equals(seanceModeleId, pk.seanceModeleId) && Objects.equals(dominanteId, pk.dominanteId);
        }
        @Override public int hashCode() { return Objects.hash(seanceModeleId, dominanteId); }
    }
}
