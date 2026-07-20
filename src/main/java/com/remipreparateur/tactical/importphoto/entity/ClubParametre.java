package com.remipreparateur.tactical.importphoto.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Surcharge par club d'un paramètre (ex. quota d'imports photo/jour relevé par le super-admin). */
@Entity
@Table(name = "club_parametre")
@IdClass(ClubParametre.Pk.class)
@Getter
@Setter
public class ClubParametre {

    @Id
    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Id
    @Column(name = "cle", nullable = false, length = 60)
    private String cle;

    @Column(name = "valeur", nullable = false)
    private String valeur;

    public static class Pk implements Serializable {
        private UUID clubId;
        private String cle;

        public Pk() {}
        public Pk(UUID clubId, String cle) { this.clubId = clubId; this.cle = cle; }

        @Override public boolean equals(Object o) {
            return o instanceof Pk pk && Objects.equals(clubId, pk.clubId) && Objects.equals(cle, pk.cle);
        }
        @Override public int hashCode() { return Objects.hash(clubId, cle); }
    }
}
