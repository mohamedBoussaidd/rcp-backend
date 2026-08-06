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
 * @param saisonId  saison consultée (null = pas de bornage temporel, comportement historique)
 */
public record ContexteActif(UUID clubId, List<UUID> equipeIds, UUID saisonId) {

    public ContexteActif {
        equipeIds = equipeIds == null ? List.of() : List.copyOf(equipeIds);
    }

    public ContexteActif(UUID clubId, List<UUID> equipeIds) {
        this(clubId, equipeIds, null);
    }

    /**
     * Aucun contexte exprimé → on retombe sur le scope identité.
     *
     * <p>La saison n'entre PAS dans ce test : elle ne désigne aucune équipe et ne peut donc pas
     * définir une portée à elle seule. Elle ne fait que borner dans le temps une portée déjà
     * résolue par le club et les équipes.</p>
     */
    public boolean estVide() {
        return clubId == null && equipeIds.isEmpty();
    }
}
