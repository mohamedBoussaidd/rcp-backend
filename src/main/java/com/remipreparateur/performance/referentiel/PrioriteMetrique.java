package com.remipreparateur.performance.referentiel;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ce qu'on accepte de sacrifier quand la charge doit baisser.
 *
 * <p>Sans cette notion, le moteur réduit TOUT proportionnellement dès que le plafond d'ACWR
 * l'oblige — et c'est physiologiquement faux. Un préparateur abandonne du volume sans hésiter,
 * jamais l'exposition à haute vitesse : c'est justement le désentraînement à la vitesse qui casse
 * les ischio-jambiers au retour. Deux réductions de 4 km peuvent donc être l'une raisonnable et
 * l'autre dangereuse, selon ce qu'on a rogné.
 *
 * <p>Trois niveaux, pas cinq : ce catalogue pilote un algorithme d'arbitrage, et trois seaux sont
 * tout ce qu'il peut exploiter de façon lisible. L'ordre de {@link #getRang()} est l'ordre dans
 * lequel on pioche pour réduire.
 *
 * <p>La priorité vit sur la PHASE d'un modèle d'objectif, jamais sur le référentiel : « le sprint
 * est prioritaire » n'est pas un fait de nature d'un latéral N1, c'est une intention d'entraîneur
 * qui change avec le moment de la saison. En accumulation le volume est roi, au pic c'est la
 * vitesse, en décharge on protège la V-max et on jette le volume.
 */
public enum PrioriteMetrique {

    /** Absorbe la coupe en premier. C'est la monnaie d'échange : typiquement le volume. */
    SECONDAIRE("Secondaire", 0),

    /** Réduit seulement une fois le secondaire épuisé. */
    IMPORTANT("Important", 1),

    /**
     * Jamais réduit. Si l'objectif devient inatteignable sans y toucher, l'application le DIT
     * au lieu de couper en silence — un arbitrage impossible est une information, pas un détail
     * d'implémentation.
     */
    INTOUCHABLE("Intouchable", 2);

    private final String libelle;
    private final int rang;

    PrioriteMetrique(String libelle, int rang) {
        this.libelle = libelle;
        this.rang = rang;
    }

    public String getLibelle() { return libelle; }

    /** Ordre de pioche : on réduit d'abord les rangs les plus bas. */
    public int getRang() { return rang; }

    /** Défaut raisonnable quand rien n'est précisé : réductible, mais pas en premier. */
    public static final PrioriteMetrique DEFAUT = IMPORTANT;

    private static final Map<String, PrioriteMetrique> PAR_NOM =
            Arrays.stream(values()).collect(Collectors.toMap(Enum::name, Function.identity()));

    public static PrioriteMetrique parNom(String nom) {
        return nom == null ? DEFAUT : PAR_NOM.getOrDefault(nom, DEFAUT);
    }
}
