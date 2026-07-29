package com.remipreparateur.ia.service;

/**
 * Résolution IA pour un contexte club : quel fournisseur, quelle clé (déchiffrée), quel modèle, et
 * si l'on est retombé sur la config globale (auquel cas les quotas par feature s'appliquent).
 *
 * <p>{@code provider} est le CODE du fournisseur (journalisé, affiché) ; {@code dialecte} est le
 * PROTOCOLE à parler, c'est lui qui choisit le client d'appel. Les deux diffèrent dès qu'on ajoute
 * un fournisseur compatible OpenAI : {@code provider=MISTRAL}, {@code dialecte=OPENAI}.
 * {@code baseUrl} vaut null pour l'API officielle du dialecte.
 */
public record IaResolved(String provider, String dialecte, String baseUrl, String cleApi,
                         String modele, boolean cleGlobale) {

    public boolean cleDisponible() {
        return cleApi != null && !cleApi.isBlank();
    }
}
