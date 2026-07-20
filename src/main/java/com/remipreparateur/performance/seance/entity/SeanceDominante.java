package com.remipreparateur.performance.seance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Dominante sélectionnée pour une séance (référentiel seedé, cf. ReferentielDominante). */
@Entity
@Table(name = "seance_dominante")
@IdClass(SeanceDominante.Pk.class)
@Getter
@Setter
public class SeanceDominante {

    @Id
    @Column(name = "seance_id", nullable = false)
    private UUID seanceId;

    @Id
    @Column(name = "dominante_id", nullable = false)
    private UUID dominanteId;

    public static class Pk implements Serializable {
        private UUID seanceId;
        private UUID dominanteId;

        public Pk() {}
        public Pk(UUID seanceId, UUID dominanteId) { this.seanceId = seanceId; this.dominanteId = dominanteId; }

        @Override public boolean equals(Object o) {
            return o instanceof Pk pk && Objects.equals(seanceId, pk.seanceId) && Objects.equals(dominanteId, pk.dominanteId);
        }
        @Override public int hashCode() { return Objects.hash(seanceId, dominanteId); }
    }
}
