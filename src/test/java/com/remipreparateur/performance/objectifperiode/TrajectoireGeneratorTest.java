package com.remipreparateur.performance.objectifperiode;

import com.remipreparateur.performance.objectifperiode.service.TrajectoireGenerator;
import com.remipreparateur.performance.objectifperiode.service.TrajectoireGenerator.Phase;
import com.remipreparateur.performance.objectifperiode.service.TrajectoireGenerator.Repartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Le générateur de trajectoire est la seule pièce du chantier dont on peut vérifier la sortie à
 * la main, sur des cas connus. Ces tests figent le comportement promis : reproduire le document
 * de référence sur six semaines, et ne détruire ni le pic ni la décharge quand la durée change.
 */
class TrajectoireGeneratorTest {

    /** Le modèle « prépa progression classique » : accumulation, développement, pic, décharge. */
    private static final List<Phase> PREPA = List.of(
            new Phase("Accumulation", 2),
            new Phase("Développement", 2),
            new Phase("Pic", 1),
            new Phase("Décharge", 1));

    private static int[] semaines(Repartition r) {
        return r.phases().stream().mapToInt(TrajectoireGenerator.PhaseRetenue::nbSemaines).toArray();
    }

    @Test
    @DisplayName("6 semaines reproduisent le document de référence : 2 / 2 / 1 / 1")
    void six_semaines_reproduisent_le_document() {
        Repartition r = TrajectoireGenerator.repartir(6, PREPA);
        assertArrayEquals(new int[]{2, 2, 1, 1}, semaines(r));
        assertTrue(r.phasesSupprimees().isEmpty());
        assertNull(r.avertissement());
    }

    @Test
    @DisplayName("9 semaines gardent le pic ET une décharge d'une semaine pleine")
    void neuf_semaines_preservent_pic_et_decharge() {
        Repartition r = TrajectoireGenerator.repartir(9, PREPA);
        assertArrayEquals(new int[]{3, 3, 2, 1}, semaines(r));
        // Le point de tout le modèle : la décharge ne se dilue pas quand la période s'allonge.
        assertEquals(1, semaines(r)[3]);
    }

    @Test
    @DisplayName("7 semaines : la semaine de plus va à l'accumulation")
    void sept_semaines() {
        assertArrayEquals(new int[]{3, 2, 1, 1}, semaines(TrajectoireGenerator.repartir(7, PREPA)));
    }

    @Test
    @DisplayName("4 semaines : une semaine par phase, rien n'est supprimé")
    void quatre_semaines() {
        Repartition r = TrajectoireGenerator.repartir(4, PREPA);
        assertArrayEquals(new int[]{1, 1, 1, 1}, semaines(r));
        assertTrue(r.phasesSupprimees().isEmpty());
    }

    @Test
    @DisplayName("5 semaines : le plancher d'une semaine est respecté partout")
    void cinq_semaines() {
        assertArrayEquals(new int[]{2, 1, 1, 1}, semaines(TrajectoireGenerator.repartir(5, PREPA)));
    }

    @Test
    @DisplayName("3 semaines : le Pic est supprimé, jamais l'accumulation ni la décharge")
    void trois_semaines_supprime_le_pic_et_le_dit() {
        Repartition r = TrajectoireGenerator.repartir(3, PREPA);
        assertEquals(3, r.phases().size());
        assertEquals(List.of("Pic"), r.phasesSupprimees());
        assertEquals("Accumulation", r.phases().get(0).nom());
        assertEquals("Décharge", r.phases().get(2).nom());
        // La suppression doit REMONTER : bricoler en silence serait le pire des comportements.
        assertNotNull(r.avertissement());
        assertTrue(r.avertissement().contains("Pic"));
    }

    @Test
    @DisplayName("Un modèle de compétition : une phase unique absorbe toute la période")
    void phase_unique() {
        Repartition r = TrajectoireGenerator.repartir(18, List.of(new Phase("Championnat", 1)));
        assertArrayEquals(new int[]{18}, semaines(r));
    }

    @Test
    @DisplayName("Aucune phase : on le dit au lieu de produire une trajectoire vide")
    void modele_sans_phase() {
        Repartition r = TrajectoireGenerator.repartir(6, List.of());
        assertTrue(r.phases().isEmpty());
        assertNotNull(r.avertissement());
    }

    @Test
    @DisplayName("L'interpolation reproduit la courbe de volume du document : 67 → 109 %")
    void interpolation_dans_la_phase() {
        // Accumulation 67 → 79 sur 2 semaines
        assertEquals(67.0, TrajectoireGenerator.pctSemaine(0, 2, 67, 79), 0.01);
        assertEquals(79.0, TrajectoireGenerator.pctSemaine(1, 2, 67, 79), 0.01);
        // Développement 91 → 100 sur 2 semaines
        assertEquals(91.0, TrajectoireGenerator.pctSemaine(0, 2, 91, 100), 0.01);
        assertEquals(100.0, TrajectoireGenerator.pctSemaine(1, 2, 91, 100), 0.01);
        // Pic : phase plate d'une semaine
        assertEquals(109.0, TrajectoireGenerator.pctSemaine(0, 1, 109, 109), 0.01);
    }

    @Test
    @DisplayName("Une phase écrasée sur une semaine prend le milieu, pas sa valeur de fin")
    void phase_ecrasee_prend_le_milieu() {
        // 79 % démarrerait la préparation au niveau d'arrivée de l'accumulation : ce serait
        // supprimer l'accumulation tout en prétendant l'avoir faite.
        assertEquals(73.0, TrajectoireGenerator.pctSemaine(0, 1, 67, 79), 0.01);
    }

    @Test
    @DisplayName("Une phase intermédiaire de fort poids survit à une phase de faible poids")
    void suppression_choisit_le_poids_le_plus_faible() {
        List<Phase> modele = List.of(
                new Phase("Reprise", 1), new Phase("Volume", 3),
                new Phase("Affûtage", 1), new Phase("Décharge", 1));
        Repartition r = TrajectoireGenerator.repartir(3, modele);
        assertEquals(List.of("Affûtage"), r.phasesSupprimees());
        assertEquals("Volume", r.phases().get(1).nom());
    }
}
