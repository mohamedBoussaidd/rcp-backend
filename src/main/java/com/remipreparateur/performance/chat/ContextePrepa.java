package com.remipreparateur.performance.chat;

import com.remipreparateur.auth.rbac.Permission;
import com.remipreparateur.performance.analytics.service.PredictionService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Contexte « préparation physique » du chat : état de la semaine (objectif hebdo, atteinte, joueurs
 * en surcharge / sous-charge), dérivé du même bundle que la carte briefing.
 *
 * <p>Premier et seul fournisseur pour l'instant. Les autres métiers (coaching, médical, direction)
 * s'ajouteront comme des {@link ContexteChat} supplémentaires, chacun gardé par sa propre permission.
 */
@Component
public class ContextePrepa implements ContexteChat {

    private final PredictionService predictionService;

    public ContextePrepa(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @Override
    public Permission permissionRequise() {
        return Permission.PREDICTIONS_READ;
    }

    @Override
    public String libelle() {
        return "Préparation physique";
    }

    @Override
    public String bloc() {
        Map<String, Object> b;
        try {
            b = asMap(predictionService.getBriefingIndicateurs());
        } catch (RuntimeException e) {
            return null;   // Python indisponible : le chat reste utilisable sur les autres contextes
        }
        int nbJoueurs = num(asMap(b.get("effectif")).get("nb_joueurs"));
        if (nbJoueurs == 0) {
            return "Aucune donnée de charge exploitable cette semaine (effectif ou GPS manquants).";
        }

        Map<String, Object> oh = asMap(b.get("objectif_hebdo"));
        Map<String, Object> ch = asMap(b.get("charge_semaine"));
        StringBuilder sb = new StringBuilder();
        sb.append("Effectif suivi : ").append(nbJoueurs).append(" joueurs (semaine en cours).\n");

        String source = str(oh.get("source"));
        Integer objManuel = numOrNull(oh.get("objectif_manuel_m"));
        Integer suggestion = numOrNull(oh.get("suggestion_moyenne_m"));
        if ("MANUEL".equals(source) && objManuel != null) {
            sb.append("Objectif de charge fixé par le préparateur : ").append(km(objManuel)).append(" km/joueur.\n");
        } else if ("INTELLIGENT".equals(source) && suggestion != null) {
            sb.append("Objectif de charge : suggestion intelligente ").append(km(suggestion))
              .append(" km/joueur en moyenne (aucun objectif manuel fixé).\n");
        } else {
            sb.append("Objectif de charge : non défini (charge chronique insuffisante).\n");
        }

        int nbAtteint = num(oh.get("nb_atteint"));
        int nbConcernes = num(oh.get("nb_concernes"));
        if (nbConcernes > 0) {
            sb.append("Atteinte : ").append(nbAtteint).append(" joueurs sur ").append(nbConcernes).append(".\n");
        }
        Map<String, Object> meilleur = asMap(oh.get("meilleur"));
        if (!meilleur.isEmpty()) {
            sb.append("Meilleur cumul : ").append(nomComplet(meilleur))
              .append(" (").append(km(num(meilleur.get("cumul_m")))).append(" km).\n");
        }

        int nbSur = num(ch.get("nb_surcharge"));
        int nbSous = num(ch.get("nb_souscharge"));
        if (nbSur > 0) sb.append("Surcharge (au-dessus du plafond ACWR) : ")
                         .append(nbSur).append(noms(asList(ch.get("surcharge")))).append(".\n");
        if (nbSous > 0) sb.append("Sous-charge (sous la cible minimale) : ")
                          .append(nbSous).append(noms(asList(ch.get("souscharge")))).append(".\n");
        if (nbSur == 0 && nbSous == 0) sb.append("Aucun joueur en surcharge ni en sous-charge marquée.\n");
        return sb.toString();
    }

    private String noms(List<Object> joueurs) {
        StringJoiner sj = new StringJoiner(", ", " (", ")");
        sj.setEmptyValue("");
        int n = 0;
        for (Object o : joueurs) {
            if (n++ >= 3) break;
            Map<String, Object> j = asMap(o);
            sj.add(nomComplet(j) + " " + km(num(j.get("cumul_m"))) + " km");
        }
        return sj.toString();
    }

    private String nomComplet(Map<String, Object> j) {
        String nom = str(j.get("nom"));
        String prenom = str(j.get("prenom"));
        if (!prenom.isBlank()) return (prenom + " " + nom).trim();
        return nom.isBlank() ? "un joueur" : nom;
    }

    private String km(int metres) {
        return String.format(Locale.FRANCE, "%.1f", metres / 1000.0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) { return o instanceof Map ? (Map<String, Object>) o : Map.of(); }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object o) { return o instanceof List ? (List<Object>) o : List.of(); }

    private static int num(Object o) { return o instanceof Number n ? n.intValue() : 0; }

    private static Integer numOrNull(Object o) { return o instanceof Number n ? n.intValue() : null; }

    private static String str(Object o) { return o == null ? "" : o.toString(); }
}
