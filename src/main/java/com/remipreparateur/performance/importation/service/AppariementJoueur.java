package com.remipreparateur.performance.importation.service;

import com.remipreparateur.joueur.entity.Joueur;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.remipreparateur.performance.importation.service.ImportNormalisation.normalise;

/**
 * Appariement d'une identité lue dans un fichier d'import (« NOM Prénom ») à une fiche joueur du club.
 * Stratégie, du plus fiable au plus permissif : alias mémorisés par club → nom complet (les deux
 * ordres, dédupliqué contre les homonymes) → prénom seul si unique dans le club.
 *
 * <p>Extrait de {@code ImportRpeService} pour être partagé par les imports RPE et Hooper : même
 * format d'identité, même dictionnaire d'alias {@code alias_joueur_import}.
 */
class AppariementJoueur {

    private final Map<String, UUID> alias;
    private final Map<String, UUID> parNomComplet = new HashMap<>();
    private final Set<String> nomsAmbigus = new HashSet<>();
    private final Map<String, List<UUID>> parPrenom = new HashMap<>();
    private final Map<UUID, String> affichage = new HashMap<>();

    AppariementJoueur(List<Joueur> joueurs, Map<String, UUID> alias) {
        this.alias = alias;
        for (Joueur j : joueurs) {
            String prenom = j.getPrenom() == null ? "" : j.getPrenom();
            String nom = j.getNom() == null ? "" : j.getNom();
            affichage.put(j.getId(), (prenom + " " + nom).trim());
            if (!nom.isBlank()) {
                indexe(normalise(prenom + " " + nom), j.getId());
                indexe(normalise(nom + " " + prenom), j.getId());
            }
            parPrenom.computeIfAbsent(normalise(prenom), k -> new ArrayList<>()).add(j.getId());
        }
    }

    private void indexe(String cle, UUID id) {
        if (cle.isBlank()) return;
        if (parNomComplet.containsKey(cle) && !parNomComplet.get(cle).equals(id)) {
            nomsAmbigus.add(cle);
        } else {
            parNomComplet.put(cle, id);
        }
    }

    UUID trouve(String identite) {
        String cle = normalise(identite);
        if (cle.isBlank()) return null;
        UUID viaAlias = alias.get(cle);
        if (viaAlias != null) return viaAlias;
        if (!nomsAmbigus.contains(cle)) {
            UUID complet = parNomComplet.get(cle);
            if (complet != null) return complet;
        }
        if (!cle.contains(" ")) { // identité mono-mot → tenter le prénom seul
            List<UUID> candidats = parPrenom.get(cle);
            if (candidats != null && candidats.size() == 1) return candidats.get(0);
        }
        return null;
    }

    String nomAffiche(UUID id) {
        return affichage.get(id);
    }

    /** Format « NOM Prénom » : premier mot = nom, reste = prénom. Renvoie [nom, prenom]. */
    static String[] decoupeNomPrenom(String identite) {
        String[] tokens = identite.trim().split("\\s+");
        if (tokens.length < 2) return new String[]{identite.trim(), ""};
        return new String[]{tokens[0], String.join(" ", Arrays.copyOfRange(tokens, 1, tokens.length))};
    }
}
