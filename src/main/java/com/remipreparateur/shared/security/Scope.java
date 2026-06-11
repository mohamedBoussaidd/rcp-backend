package com.remipreparateur.shared.security;

import java.util.List;
import java.util.UUID;

/**
 * Portee de visibilite des donnees pour l'utilisateur courant.
 * - all       : aucune restriction (super-admin)
 * - equipeIds : limite a ces equipes (president = equipes de son club ; staff/joueur = la sienne)
 * - none()    : aucun acces (all=false et equipeIds vide)
 */
public record Scope(boolean all, List<UUID> equipeIds) {

    public boolean none() {
        return !all && equipeIds.isEmpty();
    }

    public static Scope tout() {
        return new Scope(true, List.of());
    }

    public static Scope equipes(List<UUID> ids) {
        return new Scope(false, ids);
    }

    public static Scope aucun() {
        return new Scope(false, List.of());
    }
}
