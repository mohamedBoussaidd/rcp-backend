package com.remipreparateur.badge.entity;

/**
 * Présentation par défaut d'un badge. {@code INLINE} = dans le flux (à côté d'un libellé) ;
 * {@code CORNER} = pastille flottante posée dans le coin haut-droit d'un hôte {@code position:relative}.
 * C'est une valeur PAR DÉFAUT : le composant peut la surcharger au cas par cas.
 */
public enum BadgeMode {
    INLINE, CORNER
}
