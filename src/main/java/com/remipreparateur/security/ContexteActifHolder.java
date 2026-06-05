package com.remipreparateur.security;

/**
 * Porte le {@link ContexteActif} de la requête courante via un ThreadLocal,
 * sur le même modèle que le SecurityContext de Spring. Peuplé par
 * {@link ContexteFilter} en début de requête et nettoyé en fin de requête.
 */
public final class ContexteActifHolder {

    private static final ThreadLocal<ContexteActif> COURANT = new ThreadLocal<>();

    private ContexteActifHolder() {}

    public static void set(ContexteActif contexte) {
        COURANT.set(contexte);
    }

    /** Contexte de la requête courante, ou null si aucun en-tête n'a été fourni. */
    public static ContexteActif get() {
        return COURANT.get();
    }

    public static void clear() {
        COURANT.remove();
    }
}
