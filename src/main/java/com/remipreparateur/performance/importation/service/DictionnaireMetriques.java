package com.remipreparateur.performance.importation.service;

import com.remipreparateur.performance.importation.dto.MappingColonne;
import com.remipreparateur.performance.importation.dto.MetriqueImport;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dictionnaire de synonymes FR/EN pour pré-remplir le mapping colonnes→métriques au premier
 * import d'un format inconnu. Déterministe et hors-ligne : la suggestion n'est JAMAIS appliquée
 * seule, l'utilisateur valide l'écran de mapping. Les seuils de vitesse sont extraits des
 * en-têtes (« >19 km/h », « 24-28 km/h ») et rapprochés de la zone interne la plus proche
 * (15/19/24/28), le seuil réel étant conservé.
 */
@Component
public class DictionnaireMetriques {

    // « 24-28 km/h » (bande) — tirets simples ou typographiques
    private static final Pattern PLAGE = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*[-–—]\\s*(\\d+(?:[.,]\\d+)?)\\s*km/h");
    // « >19 km/h », « ≥ 25,2 km/h », « 19 km/h + » (cumul)
    private static final Pattern CUMUL = Pattern.compile("(?:[>≥]\\s*(\\d+(?:[.,]\\d+)?)|(\\d+(?:[.,]\\d+)?)\\s*km/h\\s*\\+)");

    private static final double[] SEUILS_INTERNES = {15, 19, 24, 28};
    private static final MetriqueImport[] ZONES_INTERNES =
            {MetriqueImport.DISTANCE_Z15, MetriqueImport.DISTANCE_Z19, MetriqueImport.DISTANCE_Z24, MetriqueImport.DISTANCE_Z28};

    /** Suggestion pour un en-tête normalisé, ou null si rien de reconnaissable. */
    public MappingColonne suggerer(String entete, List<String> apercu) {
        if (entete == null || entete.isBlank()) return null;

        // Colonnes techniques à ignorer explicitement (pas de suggestion).
        if (contient(entete, "capteur", "sensor", "device", "session", "period", "periode", "equipe", "team")) return null;

        if (contient(entete, "date")) return mapping(entete, MetriqueImport.DATE_SEANCE);

        // Numéro de maillot : à écarter AVANT l'identité (« numero de joueur » contient « joueur »).
        if (contient(entete, "numero", "num.", "n°") || entete.startsWith("#") && contient(entete, "joueur", "player")) return null;

        boolean nomJoueur = (entete.contains("nom") && contient(entete, "joueur", "player", "athlete"))
                || contient(entete, "player name", "athlete name", "full name")
                || entete.equals("nom") || entete.equals("joueur") || entete.equals("player")
                || entete.equals("name") || entete.equals("athlete");
        if (nomJoueur) return mapping(entete, MetriqueImport.IDENTITE);

        if (contient(entete, "temps", "duree", "duration", "time")) {
            MappingColonne m = mapping(entete, MetriqueImport.DUREE);
            m.setFormatDuree(detecteFormatDuree(entete, apercu));
            return m;
        }

        if (contient(entete, "vitesse max", "v max", "vmax", "max speed", "top speed", "peak speed")) {
            MappingColonne m = mapping(entete, MetriqueImport.VITESSE_MAX);
            if (contient(entete, "m/s")) m.setFacteur(3.6);
            return m;
        }

        if (contient(entete, "ratio", "m/min", "meterage", "metres par minute", "meters per minute")) {
            return mapping(entete, MetriqueImport.RATIO_DISTANCE_MIN);
        }

        if (contient(entete, "accel")) return mapping(entete, MetriqueImport.NB_ACCELERATIONS);
        if (contient(entete, "decel", "frein", "braking")) return mapping(entete, MetriqueImport.NB_FREINAGES);

        boolean compteur = contient(entete, "#", "nb", "nombre", "number", "count", "of sprints");
        boolean sprint = entete.contains("sprint");
        boolean distance = contient(entete, "distance", "(m)", "(km)");

        if (sprint && compteur && !distance) {
            MappingColonne m = mapping(entete, MetriqueImport.NB_SPRINTS);
            Double seuil = extraitSeuilCumul(entete);
            m.setSeuilReel(seuil);
            return m;
        }

        if (distance || sprint) {
            // Bande « a-b km/h » : mappée sur la zone interne la plus proche de a, re-cumulée à la conversion.
            Matcher plage = PLAGE.matcher(entete);
            if (plage.find()) {
                double seuil = parse(plage.group(1));
                MappingColonne m = mapping(entete, zoneLaPlusProche(seuil));
                m.setSeuilReel(seuil);
                m.setSemantique("BANDE");
                m.setFacteur(facteurDistance(entete));
                return m;
            }
            Double seuil = extraitSeuilCumul(entete);
            if (seuil != null) {
                MappingColonne m = mapping(entete, zoneLaPlusProche(seuil));
                m.setSeuilReel(seuil);
                m.setSemantique("CUMUL");
                m.setFacteur(facteurDistance(entete));
                return m;
            }
            // Familles sans seuil explicite : HID/HSR ≈ zone 19, distance de sprint ≈ zone 24.
            if (contient(entete, "hid", "hsr", "high speed", "haute intensite")) {
                MappingColonne m = mapping(entete, MetriqueImport.DISTANCE_Z19);
                m.setSemantique("CUMUL");
                m.setFacteur(facteurDistance(entete));
                return m;
            }
            if (sprint) {
                MappingColonne m = mapping(entete, MetriqueImport.DISTANCE_Z24);
                m.setSemantique("CUMUL");
                m.setFacteur(facteurDistance(entete));
                return m;
            }
            MappingColonne m = mapping(entete, MetriqueImport.DISTANCE_TOTALE);
            m.setFacteur(facteurDistance(entete));
            return m;
        }

        return null;
    }

    /* ── helpers ── */

    private MappingColonne mapping(String entete, MetriqueImport metrique) {
        MappingColonne m = new MappingColonne();
        m.setEntete(entete);
        m.setMetrique(metrique);
        return m;
    }

    private boolean contient(String entete, String... termes) {
        for (String t : termes) if (entete.contains(t)) return true;
        return false;
    }

    private Double extraitSeuilCumul(String entete) {
        Matcher m = CUMUL.matcher(entete);
        if (!m.find()) return null;
        return parse(m.group(1) != null ? m.group(1) : m.group(2));
    }

    private double parse(String s) {
        return Double.parseDouble(s.replace(',', '.'));
    }

    private MetriqueImport zoneLaPlusProche(double seuil) {
        int meilleur = 0;
        for (int i = 1; i < SEUILS_INTERNES.length; i++) {
            if (Math.abs(seuil - SEUILS_INTERNES[i]) < Math.abs(seuil - SEUILS_INTERNES[meilleur])) meilleur = i;
        }
        return ZONES_INTERNES[meilleur];
    }

    /** km → m si l'unité de l'en-tête est le kilomètre (en évitant le piège « km/h »). */
    private Double facteurDistance(String entete) {
        String sansVitesse = entete.replace("km/h", "");
        if (sansVitesse.contains("(km)") || sansVitesse.matches(".*\\bkm\\b.*")) return 1000.0;
        return null;
    }

    private String detecteFormatDuree(String entete, List<String> apercu) {
        if (apercu != null && apercu.stream().anyMatch(v -> v != null && v.contains(":"))) return "HMS";
        if (contient(entete, "(s)", "sec")) return "SECONDES";
        return "MINUTES";
    }
}
