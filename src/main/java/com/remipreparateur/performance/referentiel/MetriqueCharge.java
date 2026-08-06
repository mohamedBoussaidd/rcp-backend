package com.remipreparateur.performance.referentiel;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Vocabulaire FIGÉ des métriques de charge sur lesquelles on fixe un objectif.
 *
 * <p>Source de vérité unique partagée par le référentiel, les modèles d'objectif et le suivi :
 * une métrique n'est jamais une colonne en dur dans une table, c'est une LIGNE portant ce code.
 * Ajouter une métrique (accélérations, décélérations…) = ajouter une valeur ici, sans migration.
 *
 * <p>Toutes ces données existent DÉJÀ dans {@code donnee_gps} : ce catalogue ne demande aucun
 * nouvel import, il ne fait que nommer ce qu'on mesure depuis toujours.
 *
 * <p>Deux natures, et elles ne se comparent pas de la même façon :
 * <ul>
 *   <li>{@link Nature#CUMUL} — on additionne sur la semaine et on compare à une borne
 *       ({@code valeur_min} / {@code valeur_max} en mètres ou en nombre) ;</li>
 *   <li>{@link Nature#EXPOSITION} — un PIC, jamais un cumul. « 32 km/h » ne veut rien dire pour
 *       un joueur qui plafonne à 30 : la cible s'exprime en <b>pourcentage du record personnel</b>
 *       atteint au moins une fois dans la semaine ({@code valeur_min} = 90 par défaut).</li>
 * </ul>
 */
public enum MetriqueCharge {

    // ── Les trois métriques de tête : une par famille physiologique ──────────
    // Ce sont les SEULES affichées par défaut. Sept jauges d'un coup, personne ne les lit.
    DISTANCE_TOTALE("distance_totale", "Distance totale", "m",
            Nature.CUMUL, true, 0, "distance_totale_m"),
    DISTANCE_19("distance_19", "Distance > 19 km/h", "m",
            Nature.CUMUL, true, 1, "distance_19kmh_m"),
    EXPO_VMAX("expo_vmax", "Exposition à la vitesse max", "%",
            Nature.EXPOSITION, true, 2, "vitesse_max_kmh"),

    // ── Le détail, replié : ce sont des sous-ensembles des trois ci-dessus ───
    DISTANCE_15("distance_15", "Distance > 15 km/h", "m",
            Nature.CUMUL, false, 3, "distance_15kmh_m"),
    DISTANCE_24_28("distance_24_28", "Distance 24–28 km/h", "m",
            Nature.CUMUL, false, 4,
            // Seule métrique DÉRIVÉE : la base stocke des seuils cumulatifs (> 24 et > 28), le
            // document raisonne en tranche. GREATEST(…, 0) parce qu'un import incohérent ne doit
            // pas produire une distance négative.
            "GREATEST(COALESCE(distance_sprint_24kmh_m, 0) - COALESCE(distance_sprint_28kmh_m, 0), 0)"),
    DISTANCE_28("distance_28", "Distance > 28 km/h", "m",
            Nature.CUMUL, false, 5, "distance_sprint_28kmh_m"),
    NB_SPRINTS("nb_sprints", "Nombre de sprints (> 24 km/h)", "sprints",
            Nature.CUMUL, false, 6, "nb_sprints_24kmh");

    /** Comment la métrique se compare au réalisé. */
    public enum Nature { CUMUL, EXPOSITION }

    /** Seuil d'exposition par défaut, en % du record personnel (conseil clé du référentiel N1). */
    public static final int EXPO_VMAX_DEFAUT_PCT = 90;

    private final String code;
    private final String libelle;
    private final String unite;
    private final Nature nature;
    private final boolean principale;
    private final int ordre;
    private final String sourceGps;

    MetriqueCharge(String code, String libelle, String unite, Nature nature,
                   boolean principale, int ordre, String sourceGps) {
        this.code = code;
        this.libelle = libelle;
        this.unite = unite;
        this.nature = nature;
        this.principale = principale;
        this.ordre = ordre;
        this.sourceGps = sourceGps;
    }

    /** Code stocké en base et échangé avec le front (ex. {@code distance_19}). */
    public String getCode() { return code; }

    /** Libellé lisible (FR) pour l'UI. */
    public String getLibelle() { return libelle; }

    /** Unité d'affichage : {@code m}, {@code sprints} ou {@code %}. */
    public String getUnite() { return unite; }

    public Nature getNature() { return nature; }

    /** Affichée d'office ; les autres vivent derrière un dépliant. */
    public boolean isPrincipale() { return principale; }

    public int getOrdre() { return ordre; }

    /** Colonne de {@code donnee_gps}, ou expression SQL pour la seule métrique dérivée. */
    public String getSourceGps() { return sourceGps; }

    /** Vrai si la métrique s'additionne sur la semaine (par opposition à un pic). */
    public boolean estCumulative() { return nature == Nature.CUMUL; }

    /** Les trois métriques de tête, dans l'ordre d'affichage. */
    public static List<MetriqueCharge> principales() {
        return Arrays.stream(values()).filter(MetriqueCharge::isPrincipale)
                .sorted(java.util.Comparator.comparingInt(MetriqueCharge::getOrdre)).toList();
    }

    /** Toutes les métriques, dans l'ordre d'affichage. */
    public static List<MetriqueCharge> toutes() {
        return Arrays.stream(values())
                .sorted(java.util.Comparator.comparingInt(MetriqueCharge::getOrdre)).toList();
    }

    private static final Map<String, MetriqueCharge> PAR_CODE =
            Arrays.stream(values()).collect(Collectors.toMap(MetriqueCharge::getCode, Function.identity()));

    /** Retrouve une métrique par son code, ou {@code null} si inconnu. */
    public static MetriqueCharge parCode(String code) {
        return code == null ? null : PAR_CODE.get(code);
    }
}
