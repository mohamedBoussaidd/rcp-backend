package com.remipreparateur.shared.security;

import java.util.List;
import java.util.UUID;

/**
 * Contexte de navigation actif transmis par le client (en-têtes HTTP) :
 * club courant et, optionnellement, sous-ensemble d'équipes ciblées.
 * Sert UNIQUEMENT à restreindre la portée des données dans le scope autorisé
 * par l'identité (cf. {@link ScopeResolver}) — jamais à l'élargir.
 *
 * @param clubId    club actif (null = aucun contexte de club)
 * @param equipeIds équipes ciblées (vide = toutes les équipes du club actif)
 */
public record ContexteActif(UUID clubId, List<UUID> equipeIds) {

    public ContexteActif {
        equipeIds = equipeIds == null ? List.of() : List.copyOf(equipeIds);
    }

    /** Aucun contexte exprimé (ni club ni équipes) → on retombe sur le scope identité. */
    public boolean estVide() {
        return clubId == null && equipeIds.isEmpty();
    }
}
