package com.remipreparateur.performance.importation.service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalisation partagée de l'import GPS : en-têtes, identités joueur, nombres, durées.
 * La signature d'en-têtes produite ici DOIT rester identique à celle des seeds SQL
 * (profil global McLloyd en V56) : NFKD sans marques combinantes ('²'→'2'), minuscules,
 * espaces réduits.
 */
public final class ImportNormalisation {

    private ImportNormalisation() {}

    private static final Pattern MARQUES = Pattern.compile("\\p{M}+");
    private static final Pattern ESPACES = Pattern.compile("\\s+");
    private static final Pattern DUREE_HMS = Pattern.compile("^(\\d{1,3}):(\\d{1,2})(?::(\\d{1,2}))?$");

    public static String normalise(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFKD);
        n = MARQUES.matcher(n).replaceAll("");
        return ESPACES.matcher(n.toLowerCase(Locale.ROOT).trim()).replaceAll(" ");
    }

    public static String signature(List<String> entetes) {
        return String.join("|", entetes.stream().map(ImportNormalisation::normalise).toList());
    }

    /**
     * Parse un nombre en tolérant la virgule décimale française et les séparateurs de milliers.
     * Renvoie null si la cellule n'est pas numérique.
     */
    public static BigDecimal parseNombre(String brut) {
        if (brut == null) return null;
        String s = brut.trim().replace(" ", "").replace(" ", "");
        if (s.isEmpty()) return null;
        boolean point = s.indexOf('.') >= 0, virgule = s.indexOf(',') >= 0;
        if (point && virgule) {
            // Le dernier séparateur rencontré est le décimal, l'autre sépare les milliers.
            if (s.lastIndexOf(',') > s.lastIndexOf('.')) s = s.replace(".", "").replace(',', '.');
            else s = s.replace(",", "");
        } else if (virgule) {
            s = s.replace(',', '.');
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Durée en minutes selon le format déclaré du mapping : HMS ("01:07:20" → 67), ou
     * numérique en MINUTES / SECONDES. HMS à deux composantes est lu mm:ss.
     */
    public static BigDecimal parseDureeMinutes(String brut, String formatDuree) {
        if (brut == null || brut.isBlank()) return null;
        Matcher m = DUREE_HMS.matcher(brut.trim());
        if (m.matches()) {
            long a = Long.parseLong(m.group(1));
            long b = Long.parseLong(m.group(2));
            if (m.group(3) != null) { // h:mm:ss
                return BigDecimal.valueOf(a * 60 + b + Math.round(Long.parseLong(m.group(3)) / 60.0));
            }
            return BigDecimal.valueOf(a + Math.round(b / 60.0)); // mm:ss
        }
        BigDecimal n = parseNombre(brut);
        if (n == null) return null;
        if ("SECONDES".equals(formatDuree)) return n.divide(BigDecimal.valueOf(60), 0, java.math.RoundingMode.HALF_UP);
        return n; // MINUTES (défaut)
    }
}
