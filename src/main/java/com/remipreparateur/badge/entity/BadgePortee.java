package com.remipreparateur.badge.entity;

/**
 * Portée d'un badge.
 * {@code SYSTEME}    = placé par le code (clé référencée), édition mais pas de suppression ;
 *                      couleur résolue par le ton (réajustable par le club).
 * {@code PLATEFORME} = tag créé par le super-admin, assigné à des entités, visible par tous les
 *                      clubs ; couleur explicite fixe (jamais recolorée côté club).
 */
public enum BadgePortee {
    SYSTEME, PLATEFORME
}
