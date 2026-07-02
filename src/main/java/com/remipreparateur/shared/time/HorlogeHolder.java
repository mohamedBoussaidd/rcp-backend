package com.remipreparateur.shared.time;

import java.time.LocalDate;

/**
 * Porte la « date simulée » de la requête courante via un ThreadLocal, sur le même modèle que
 * {@code ContexteActifHolder}. Peuplé par {@link HorlogeFilter} en début de requête (depuis l'en-tête
 * {@code X-Date-Simulee}) et nettoyé en fin de requête. La valeur n'est PAS encore filtrée par rôle :
 * c'est {@link Horloge} qui ne l'honore que pour un SUPER_ADMIN.
 */
public final class HorlogeHolder {

    private static final ThreadLocal<LocalDate> DATE_SIMULEE = new ThreadLocal<>();

    private HorlogeHolder() {}

    public static void set(LocalDate date) {
        DATE_SIMULEE.set(date);
    }

    /** Date simulée de la requête courante, ou null si aucun en-tête valide n'a été fourni. */
    public static LocalDate get() {
        return DATE_SIMULEE.get();
    }

    public static void clear() {
        DATE_SIMULEE.remove();
    }
}
