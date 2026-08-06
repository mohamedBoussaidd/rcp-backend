package com.remipreparateur.performance.objectifperiode;

import com.remipreparateur.performance.objectifperiode.service.ArbitrageSemaineService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Invariant du relissage : ce qu'on retire à la semaine à deux matchs se retrouve INTÉGRALEMENT
 * sur les semaines suivantes. Une division entière naïve perdrait un mètre par-ci par-là — sans
 * conséquence visible sur un écran, mais le bilan de période ne tomberait plus juste et personne
 * ne saurait dire pourquoi.
 */
class ArbitrageRepartitionTest {

    private static int somme(int[] parts) {
        return Arrays.stream(parts).sum();
    }

    @Test
    @DisplayName("Deux semaines, charge paire : moitié-moitié")
    void deuxSemainesPaire() {
        int[] parts = ArbitrageSemaineService.repartir(10_000, 2);
        assertArrayEquals(new int[]{5_000, 5_000}, parts);
        assertEquals(10_000, somme(parts));
    }

    @Test
    @DisplayName("Deux semaines, charge impaire : le reste va sur la dernière, rien ne se perd")
    void deuxSemainesImpaire() {
        int[] parts = ArbitrageSemaineService.repartir(10_001, 2);
        assertArrayEquals(new int[]{5_000, 5_001}, parts);
        assertEquals(10_001, somme(parts));
    }

    @Test
    @DisplayName("Une seule semaine disponible : elle prend tout")
    void uneSeuleSemaine() {
        int[] parts = ArbitrageSemaineService.repartir(9_450, 1);
        assertArrayEquals(new int[]{9_450}, parts);
    }

    @Test
    @DisplayName("Aucune semaine cible : aucune part, et surtout pas de division par zéro")
    void aucuneCible() {
        assertEquals(0, ArbitrageSemaineService.repartir(9_450, 0).length);
    }

    @Test
    @DisplayName("La somme reste exacte quelle que soit la charge, sur 1 à 4 semaines")
    void sommeToujoursExacte() {
        for (int cout = 1; cout <= 12_345; cout += 37) {
            for (int n = 1; n <= 4; n++) {
                assertEquals(cout, somme(ArbitrageSemaineService.repartir(cout, n)),
                        "cout=" + cout + " n=" + n);
            }
        }
    }
}
