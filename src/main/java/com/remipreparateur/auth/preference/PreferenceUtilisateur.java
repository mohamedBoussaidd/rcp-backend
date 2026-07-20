package com.remipreparateur.auth.preference;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Préférence d'interface d'UN utilisateur (clé/valeur libre) : mode avancé des séances,
 * style de rendu des schémas… Rien de sensible ni de partagé — chaque compte ne lit et
 * n'écrit que les siennes (self-scope, cf. PreferenceController).
 */
@Entity
@Table(name = "preference_utilisateur")
@IdClass(PreferenceUtilisateur.Pk.class)
@Getter
@Setter
public class PreferenceUtilisateur {

    @Id
    @Column(name = "utilisateur_id", nullable = false)
    private UUID utilisateurId;

    @Id
    @Column(name = "cle", nullable = false, length = 60)
    private String cle;

    @Column(name = "valeur", nullable = false)
    private String valeur;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public static class Pk implements Serializable {
        private UUID utilisateurId;
        private String cle;

        public Pk() {}
        public Pk(UUID utilisateurId, String cle) { this.utilisateurId = utilisateurId; this.cle = cle; }

        @Override public boolean equals(Object o) {
            return o instanceof Pk pk && Objects.equals(utilisateurId, pk.utilisateurId) && Objects.equals(cle, pk.cle);
        }
        @Override public int hashCode() { return Objects.hash(utilisateurId, cle); }
    }
}
