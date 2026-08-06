package com.remipreparateur.performance.objectifperiode.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Répartition des semaines d'une période entre les phases d'un modèle, et interpolation à
 * l'intérieur de chaque phase.
 *
 * <p>Logique PURE (aucune dépendance Spring, aucune base) : c'est le cœur du lot 2 et la seule
 * partie qu'on veut pouvoir vérifier à la main sur des exemples connus.
 *
 * <p><b>La règle qui justifie tout le modèle en phases</b> : on interpole à l'intérieur d'une
 * phase, jamais entre deux phases. Étirer linéairement six semaines fixes sur neuf détruit
 * simultanément le pic (moyenné avec ses voisines, la valeur maximale n'est plus jamais atteinte)
 * et la décharge (étalée sur deux semaines, ce n'est plus une chute mais un ralentissement).
 * Ici chaque phase reçoit son propre bloc de semaines, donc la décharge reste une décharge.
 *
 * <p>Vérification sur le document de référence — poids 2/2/1/1 :
 * <pre>
 *   6 semaines → 2 / 2 / 1 / 1   (reproduit le document au chiffre près)
 *   7 semaines → 3 / 2 / 1 / 1
 *   9 semaines → 3 / 3 / 2 / 1   (le pic est intact, la décharge fait une semaine pleine)
 *   4 semaines → 1 / 1 / 1 / 1
 *   3 semaines → 1 / 1 / — / 1   (une phase est supprimée, et l'appelant le DIT)
 * </pre>
 */
public final class TrajectoireGenerator {

    private TrajectoireGenerator() {}

    /** Une phase telle que le générateur la consomme. */
    public record Phase(String nom, int poidsDuree) {}

    /** Répartition obtenue : les phases retenues, leurs semaines, et ce qu'on a dû abandonner. */
    public record Repartition(List<PhaseRetenue> phases, List<String> phasesSupprimees,
                              String avertissement) {}

    public record PhaseRetenue(int indexOrigine, String nom, int nbSemaines) {}

    /**
     * Répartit {@code nbSemaines} entre les phases, proportionnellement à leurs poids, avec un
     * PLANCHER d'une semaine par phase.
     *
     * <p>Si la période est trop courte pour toutes les phases, on en supprime — mais jamais au
     * hasard : la PREMIÈRE et la DERNIÈRE sont protégées (on ne commence pas une préparation par
     * son pic, et on n'arrive pas en compétition sans décharge), et on retire d'abord les phases
     * intermédiaires de plus faible poids. La suppression est remontée à l'appelant pour être
     * affichée, jamais absorbée en silence.
     */
    public static Repartition repartir(int nbSemaines, List<Phase> phases) {
        if (phases == null || phases.isEmpty()) {
            return new Repartition(List.of(), List.of(), "Ce modèle ne contient aucune phase.");
        }
        int n = Math.max(1, nbSemaines);

        List<Integer> retenus = new ArrayList<>();
        for (int i = 0; i < phases.size(); i++) retenus.add(i);
        List<String> supprimees = new ArrayList<>();

        // Trop de phases pour le nombre de semaines : on sacrifie l'intérieur, pas les bords.
        while (retenus.size() > n && retenus.size() > 1) {
            int aRetirer = -1;
            int poidsMin = Integer.MAX_VALUE;
            for (int pos = 1; pos < retenus.size() - 1; pos++) {
                int poids = phases.get(retenus.get(pos)).poidsDuree();
                if (poids < poidsMin) { poidsMin = poids; aRetirer = pos; }
            }
            if (aRetirer < 0) aRetirer = retenus.size() - 1;   // 2 phases pour 1 semaine
            supprimees.add(phases.get(retenus.get(aRetirer)).nom());
            retenus.remove(aRetirer);
        }

        int total = 0;
        for (int i : retenus) total += Math.max(1, phases.get(i).poidsDuree());

        // Plus grand reste, avec plancher à 1. Une phase remontée par le plancher ne participe
        // plus à la distribution du reste : elle a déjà reçu plus que sa part.
        int[] base = new int[retenus.size()];
        double[] frac = new double[retenus.size()];
        boolean[] planchee = new boolean[retenus.size()];
        int somme = 0;
        for (int k = 0; k < retenus.size(); k++) {
            double exact = (double) n * Math.max(1, phases.get(retenus.get(k)).poidsDuree()) / total;
            int plancher = (int) Math.floor(exact);
            if (plancher < 1) { plancher = 1; planchee[k] = true; }
            base[k] = plancher;
            frac[k] = exact - Math.floor(exact);
            somme += plancher;
        }

        int reste = n - somme;
        if (reste > 0) {
            List<Integer> ordre = new ArrayList<>();
            for (int k = 0; k < retenus.size(); k++) if (!planchee[k]) ordre.add(k);
            // Plus forte fraction d'abord ; à égalité, la phase la plus précoce (le début de la
            // période absorbe mieux une semaine de plus qu'une décharge qu'on doublerait).
            ordre.sort(Comparator.<Integer>comparingDouble(k -> -frac[k]).thenComparingInt(k -> k));
            for (int i = 0; i < reste; i++) {
                if (ordre.isEmpty()) { base[0]++; continue; }
                base[ordre.get(i % ordre.size())]++;
            }
        } else if (reste < 0) {
            // Le plancher a fait déborder : on reprend aux phases les plus longues.
            for (int i = 0; i < -reste; i++) {
                int plusLongue = 0;
                for (int k = 1; k < base.length; k++) if (base[k] > base[plusLongue]) plusLongue = k;
                if (base[plusLongue] > 1) base[plusLongue]--;
            }
        }

        List<PhaseRetenue> resultat = new ArrayList<>();
        for (int k = 0; k < retenus.size(); k++) {
            int i = retenus.get(k);
            resultat.add(new PhaseRetenue(i, phases.get(i).nom(), base[k]));
        }

        String avertissement = supprimees.isEmpty() ? null
                : n + " semaine" + (n > 1 ? "s" : "") + " pour " + phases.size()
                  + " phases : « " + String.join(" », « ", supprimees) + " » "
                  + (supprimees.size() > 1 ? "ont été supprimées." : "a été supprimée.");
        return new Repartition(resultat, supprimees, avertissement);
    }

    /**
     * Niveau (en %) de la {@code index}-ième semaine d'une phase de {@code nbSemaines} semaines,
     * interpolé linéairement entre {@code pctDebut} et {@code pctFin}.
     *
     * <p>Une phase d'UNE seule semaine prend le MILIEU de sa fourchette, pas sa fin : une
     * accumulation 67 → 79 % écrasée sur une semaine démarrerait la préparation à 79 %, ce qui
     * revient à supprimer l'accumulation tout en prétendant l'avoir faite.
     */
    public static double pctSemaine(int index, int nbSemaines, double pctDebut, double pctFin) {
        if (nbSemaines <= 1) return (pctDebut + pctFin) / 2.0;
        double t = (double) index / (nbSemaines - 1);
        return pctDebut + (pctFin - pctDebut) * t;
    }
}
