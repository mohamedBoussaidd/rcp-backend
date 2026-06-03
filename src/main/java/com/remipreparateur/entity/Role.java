package com.remipreparateur.entity;

/**
 * Roles applicatifs — 1 seul role par utilisateur (v1).
 * Hierarchie : SUPER_ADMIN > PRESIDENT (admin club) > staff/joueurs d'une equipe.
 */
public enum Role {
    SUPER_ADMIN,   // toi, global, hors club
    PRESIDENT,     // admin d'UN club
    ENTRAINEUR,    // planif seances
    PREPARATEUR,   // seances physiques + import/GPS
    MEDICAL,       // blessures + lecture seances (specialite: kine/medecin/osteo)
    ADMINISTRATIF, // secretaire/tresorier (acces a definir plus tard)
    JOUEUR         // espace perso
}
