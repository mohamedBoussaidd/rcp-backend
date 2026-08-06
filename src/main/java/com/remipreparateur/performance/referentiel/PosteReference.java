package com.remipreparateur.performance.referentiel;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Postes de RÉFÉRENCE : la maille à laquelle les préconisations de charge sont publiées.
 *
 * <p>Volontairement plus GROSSIÈRE que les postes de la fiche joueur. Les référentiels du métier
 * (et le document N1 qui sert de seed) ne distinguent pas un latéral droit d'un latéral gauche,
 * ni un milieu défensif d'un milieu offensif : physiologiquement, ce sont les mêmes exigences.
 * Publier onze lignes reviendrait à demander au super-admin d'inventer six valeurs qu'aucune
 * source ne fournit.
 *
 * <p>{@link #parPosteJoueur(String)} fait donc le rabattement 11 → 6. Sans lui, le seed N1 ne
 * couvrirait que la moitié d'un effectif : les latéraux droits auraient une cible, les gauches
 * non. La normalisation d'entrée (abréviations, casse, espaces) reprend celle de
 * {@code POSTE_ALIASES} côté Python, pour que les deux moteurs rangent un « LD » au même endroit.
 */
public enum PosteReference {

    GARDIEN("gardien", "Gardien", 0),
    DEFENSEUR_CENTRAL("defenseur_central", "Défenseur central", 1),
    LATERAL("lateral", "Latéral", 2),
    MILIEU_AXIAL("milieu_axial", "Milieu axial", 3),
    AILIER("ailier", "Ailier", 4),
    ATTAQUANT("attaquant", "Attaquant", 5);

    private final String code;
    private final String libelle;
    private final int ordre;

    PosteReference(String code, String libelle, int ordre) {
        this.code = code;
        this.libelle = libelle;
        this.ordre = ordre;
    }

    public String getCode() { return code; }
    public String getLibelle() { return libelle; }
    public int getOrdre() { return ordre; }

    /** Tous les postes, dans l'ordre d'affichage (du but vers l'attaque). */
    public static List<PosteReference> toutes() {
        return Arrays.stream(values())
                .sorted(java.util.Comparator.comparingInt(PosteReference::getOrdre)).toList();
    }

    private static final Map<String, PosteReference> PAR_CODE =
            Arrays.stream(values()).collect(Collectors.toMap(PosteReference::getCode, Function.identity()));

    public static PosteReference parCode(String code) {
        return code == null ? null : PAR_CODE.get(code);
    }

    /** Abréviations usuelles → poste canonique de la fiche joueur (miroir de {@code POSTE_ALIASES}). */
    private static final Map<String, String> ALIAS = Map.ofEntries(
            Map.entry("g", "gardien"), Map.entry("gk", "gardien"),
            Map.entry("gd", "gardien"), Map.entry("goal", "gardien"),
            Map.entry("dc", "defenseur_central"), Map.entry("cb", "defenseur_central"),
            Map.entry("def", "defenseur_central"),
            Map.entry("lb", "lateral_gauche"), Map.entry("lg", "lateral_gauche"),
            Map.entry("rb", "lateral_droit"), Map.entry("ld", "lateral_droit"),
            Map.entry("md", "milieu_defensif"), Map.entry("mdc", "milieu_defensif"),
            Map.entry("cdm", "milieu_defensif"), Map.entry("dmc", "milieu_defensif"),
            Map.entry("mc", "milieu_central"), Map.entry("cm", "milieu_central"),
            Map.entry("mf", "milieu_central"),
            Map.entry("mo", "milieu_offensif"), Map.entry("moff", "milieu_offensif"),
            Map.entry("cam", "milieu_offensif"),
            Map.entry("ag", "ailier_gauche"), Map.entry("aig", "ailier_gauche"),
            Map.entry("lw", "ailier_gauche"),
            Map.entry("ad", "ailier_droit"), Map.entry("aid", "ailier_droit"),
            Map.entry("rw", "ailier_droit"),
            Map.entry("att", "attaquant"), Map.entry("st", "attaquant"),
            Map.entry("fw", "attaquant"),
            Map.entry("ac", "avant_centre"), Map.entry("cf", "avant_centre"),
            Map.entry("9", "avant_centre"));

    /** Poste canonique de la fiche joueur → poste de référence (le rabattement 11 → 6). */
    private static final Map<String, PosteReference> RABATTEMENT = Map.ofEntries(
            Map.entry("gardien", GARDIEN),
            Map.entry("defenseur_central", DEFENSEUR_CENTRAL),
            Map.entry("lateral_droit", LATERAL),
            Map.entry("lateral_gauche", LATERAL),
            Map.entry("milieu_defensif", MILIEU_AXIAL),
            Map.entry("milieu_central", MILIEU_AXIAL),
            Map.entry("milieu_offensif", MILIEU_AXIAL),
            Map.entry("ailier_droit", AILIER),
            Map.entry("ailier_gauche", AILIER),
            Map.entry("attaquant", ATTAQUANT),
            Map.entry("avant_centre", ATTAQUANT));

    /**
     * Poste de référence d'un joueur à partir du libellé stocké sur sa fiche.
     *
     * <p>Renvoie {@code null} si le poste est vide ou inconnu — l'appelant décide alors quoi
     * faire (typiquement : pas de cible affichée, jamais une cible fausse). Un poste absent est
     * une donnée manquante, pas un défenseur central.
     */
    public static PosteReference parPosteJoueur(String posteFiche) {
        if (posteFiche == null || posteFiche.isBlank()) return null;
        String cle = posteFiche.trim().toLowerCase().replace(' ', '_').replace('-', '_');
        cle = ALIAS.getOrDefault(cle, cle);
        return RABATTEMENT.get(cle);
    }
}
