package com.remipreparateur.badge.entity;

/** Type d'entité pouvant porter des tags (badges plateforme). Extensible sans nouvelle table. */
public enum TypeEntite {
    EXERCICE, SEANCE, JOUEUR,
    /** Référentiel de charge : permet au super-admin de marquer « officiel », « à valider »… */
    REFERENTIEL
}
