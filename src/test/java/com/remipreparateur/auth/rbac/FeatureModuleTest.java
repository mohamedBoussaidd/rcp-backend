package com.remipreparateur.auth.rbac;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Garde-fous du câblage produit des permissions ↔ modules. Le point sensible : une permission
 * dont le module add-on est coupé doit être retirée à la volée (PermissionResolver s'appuie sur
 * {@link FeatureModule#modulesDe}). Vérifie en particulier le générateur de séance IA.
 */
class FeatureModuleTest {

    @Test
    void toutesLesPermissionsSontMappeesSurUnModule() {
        // `of()` est un switch exhaustif : aucune permission ne doit lever (garde-fou anti-oubli).
        assertThatCode(() -> {
            for (Permission p : Permission.values()) {
                assertThat(FeatureModule.of(p)).isNotNull();
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void generateurSeanceIaEstUnAddonGouvernantSaPermission() {
        FeatureModule m = FeatureModule.GENERATEUR_SEANCE_IA;

        // Add-on : jamais socle, présent dans le catalogue des modules activables.
        assertThat(m.isSocle()).isFalse();
        assertThat(FeatureModule.activableCodes()).contains("generateur_seance_ia");

        // La permission est gouvernée par ce seul module → coupé, la permission tombe.
        assertThat(FeatureModule.of(Permission.SEANCE_IA_GENERATE)).isEqualTo(m);
        assertThat(FeatureModule.modulesDe(Permission.SEANCE_IA_GENERATE))
                .containsExactly(FeatureModule.GENERATEUR_SEANCE_IA);
    }
}
