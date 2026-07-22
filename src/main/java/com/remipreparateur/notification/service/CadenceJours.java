package com.remipreparateur.notification.service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Cadence d'un rappel/bloc exprimée en jours de la semaine (ISO : 1 = lundi … 7 = dimanche),
 * stockée en CSV dans {@code notif_config_equipe} (ex. « 1,4 » = lundi + jeudi). Une chaîne
 * vide ou nulle = jamais.
 */
public final class CadenceJours {

    private CadenceJours() {}

    /** Le jour de {@code date} fait-il partie de la cadence CSV ? */
    public static boolean actifLe(String csvJours, LocalDate date) {
        if (csvJours == null || csvJours.isBlank()) return false;
        return parse(csvJours).contains(date.getDayOfWeek().getValue());
    }

    /** Ensemble ordonné des jours ISO valides (1..7) d'un CSV ; ignore les entrées invalides. */
    public static Set<Integer> parse(String csvJours) {
        Set<Integer> jours = new LinkedHashSet<>();
        if (csvJours == null || csvJours.isBlank()) return jours;
        Arrays.stream(csvJours.split(",")).forEach(s -> {
            try {
                int v = Integer.parseInt(s.trim());
                if (v >= 1 && v <= 7) jours.add(v);
            } catch (NumberFormatException ignore) { }
        });
        return jours;
    }
}
