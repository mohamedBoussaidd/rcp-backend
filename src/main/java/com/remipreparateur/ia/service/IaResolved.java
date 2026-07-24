package com.remipreparateur.ia.service;

/**
 * Résolution IA pour un contexte club : quel provider, quelle clé (déchiffrée) et quel modèle
 * utiliser, et si l'on est retombé sur la clé globale (auquel cas les quotas par feature s'appliquent).
 */
public record IaResolved(String provider, String cleApi, String modele, boolean cleGlobale) {

    public boolean cleDisponible() {
        return cleApi != null && !cleApi.isBlank();
    }
}
