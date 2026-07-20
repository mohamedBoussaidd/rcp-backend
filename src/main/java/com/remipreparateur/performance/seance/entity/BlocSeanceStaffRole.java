package com.remipreparateur.performance.seance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Rôle tenu par un membre du staff sur un bloc de séance (V66) : ▶ mène le bloc, ⚖ arbitre,
 * ⚽ source de balle, ⏱ chrono, 👁 observation, 🩺 soins.
 *
 * <p>Le cumul est libre — en club amateur, celui qui mène l'atelier arbitre souvent aussi, et
 * l'interdire obligerait à mentir sur la fiche. Seule contrainte : <b>un seul MENEUR par bloc</b>,
 * garantie par un index unique partiel en base ({@code idx_bloc_meneur_unique}), parce que deux
 * personnes qui mènent le même atelier produisent une consigne contradictoire sur le terrain.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BlocSeanceStaffRole {

    @Column(name = "utilisateur_id", nullable = false)
    private UUID utilisateurId;

    /** Code de {@code referentiel_role_bloc} (liste figée, commune à tous les clubs). */
    @Column(name = "role", nullable = false, length = 20)
    private String role;
}
