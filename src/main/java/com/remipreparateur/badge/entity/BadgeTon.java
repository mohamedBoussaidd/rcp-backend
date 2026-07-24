package com.remipreparateur.badge.entity;

/**
 * Famille de couleur d'un badge (paire {fond, texte} définie dans {@code badge_palette_ton}).
 * Les badges SYSTÈME sont colorés par leur ton — donc réajustables par un club qui surcharge le ton.
 */
public enum BadgeTon {
    NEUTRAL, INFO, SUCCESS, WARNING, DANGER, BRAND
}
