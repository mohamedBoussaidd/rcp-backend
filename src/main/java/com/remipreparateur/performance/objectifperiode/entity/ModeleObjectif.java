package com.remipreparateur.performance.objectifperiode.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Modèle d'objectif réutilisable d'un club : la FORME d'une période, sans ses kilomètres.
 *
 * <p>Le modèle ne stocke JAMAIS de valeurs absolues, mais des pourcentages de la cible du
 * référentiel. Conséquence : le même modèle « Prépa — progression classique » sert un club N1
 * (33 000 m de référence hebdo) et un club régional (29 000 m), chacun obtenant ses propres
 * kilomètres. <b>Le modèle porte la forme, le référentiel porte l'échelle</b> — sans quoi tout
 * modèle serait en réalité un modèle d'un seul niveau, à re-saisir intégralement pour le voisin.
 *
 * <p>Le pourcentage est une décision de STOCKAGE, pas d'affichage : l'éditeur montre et accepte
 * des mètres, la conversion reste invisible.
 *
 * @see ModeleObjectifPhase pour la raison d'être des phases (protéger le pic et la décharge)
 */
@Entity
@Table(name = "modele_objectif")
@Getter
@Setter
public class ModeleObjectif {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(name = "nom", nullable = false)
    private String nom;

    /**
     * Type de période auquel ce modèle s'applique : PREPARATION | COMPETITION | REPRISE.
     * Même vocabulaire que {@code periode_saison.type}, pour ne proposer que des modèles
     * pertinents au moment d'instancier.
     */
    @Column(name = "type_periode", nullable = false)
    private String typePeriode;

    @Column(name = "cree_par")
    private UUID creePar;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
