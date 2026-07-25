package com.remipreparateur.performance.briefing;

import com.remipreparateur.auth.entity.Role;
import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.ia.service.CarteIaService;
import com.remipreparateur.performance.analytics.service.PredictionService;
import com.remipreparateur.shared.security.ContexteActif;
import com.remipreparateur.shared.security.ContexteActifHolder;
import com.remipreparateur.shared.security.CurrentUserProvider;
import com.remipreparateur.tactical.importphoto.service.ParametreIaService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * Carte IA « briefing du préparateur » (note du prépa). Récupère les INDICATEURS déjà calculés côté
 * Python (atteinte de l'objectif hebdo, joueurs en surcharge/sous-charge), les met en forme en un bloc
 * factuel, puis délègue au {@link CarteIaService} : mise en mots par le LLM si disponible, sinon repli
 * sur un gabarit local (mêmes chiffres, phrasé fixe). Aucune donnée brute ne part au LLM.
 */
@Service
public class BriefingService {

    /** Identifiant de la carte : quota + toggle LLM + prompt (voir {@link ParametreIaService}). */
    public static final String FEATURE = "briefing_prepa";

    private final PredictionService predictionService;
    private final CarteIaService carteIa;
    private final CurrentUserProvider currentUser;

    public BriefingService(PredictionService predictionService, CarteIaService carteIa,
                           CurrentUserProvider currentUser) {
        this.predictionService = predictionService;
        this.carteIa = carteIa;
        this.currentUser = currentUser;
    }

    public CarteIaService.TexteCarte generer() {
        Map<String, Object> b = asMap(predictionService.getBriefingIndicateurs());
        UUID clubId = clubCourant();
        String indicateurs = construireIndicateurs(b);
        return carteIa.produire(clubId, FEATURE, ParametreIaService.CLE_PROMPT_BRIEFING_PREPA,
                indicateurs, () -> gabarit(b));
    }

    // ── Bloc d'indicateurs factuels envoyé au LLM (message utilisateur) ──

    private String construireIndicateurs(Map<String, Object> b) {
        Map<String, Object> effectif = asMap(b.get("effectif"));
        int nbJoueurs = num(effectif.get("nb_joueurs"));
        if (nbJoueurs == 0) {
            return "CONTEXTE : briefing hebdomadaire de l'équipe.\n"
                    + "Aucune donnée exploitable cette semaine (effectif ou données GPS manquants).";
        }

        Map<String, Object> oh = asMap(b.get("objectif_hebdo"));
        Map<String, Object> ch = asMap(b.get("charge_semaine"));

        StringBuilder sb = new StringBuilder();
        sb.append("CONTEXTE : briefing hebdomadaire de l'équipe (semaine en cours).\n");
        if (Boolean.TRUE.equals(b.get("multi_equipes"))) {
            sb.append("Périmètre : plusieurs équipes agrégées (objectif manuel non applicable).\n");
        }
        sb.append("Effectif suivi : ").append(nbJoueurs).append(" joueurs.\n");

        String source = str(oh.get("source"));
        Integer objManuel = numOrNull(oh.get("objectif_manuel_m"));
        Integer suggestion = numOrNull(oh.get("suggestion_moyenne_m"));
        if ("MANUEL".equals(source) && objManuel != null) {
            sb.append("Objectif de charge : ").append(km(objManuel)).append(" km/joueur (fixé par le préparateur).\n");
        } else if ("INTELLIGENT".equals(source) && suggestion != null) {
            sb.append("Objectif de charge : suggestion intelligente ").append(km(suggestion))
              .append(" km/joueur en moyenne (aucun objectif manuel fixé).\n");
        } else {
            sb.append("Objectif de charge : non défini (données de charge chronique insuffisantes).\n");
        }

        int nbAtteint = num(oh.get("nb_atteint"));
        int nbConcernes = num(oh.get("nb_concernes"));
        Integer resteMoyen = numOrNull(oh.get("reste_moyen_m"));
        if (nbConcernes > 0) {
            sb.append("Atteinte : ").append(nbAtteint).append(" joueurs sur ").append(nbConcernes)
              .append(" ont atteint l'objectif");
            if (resteMoyen != null && resteMoyen > 0) {
                sb.append(" ; il reste en moyenne ").append(km(resteMoyen)).append(" km à parcourir aux autres");
            }
            sb.append(".\n");
        } else {
            sb.append("Atteinte : non évaluable (pas d'objectif exploitable par joueur).\n");
        }

        Map<String, Object> meilleur = asMap(oh.get("meilleur"));
        if (!meilleur.isEmpty()) {
            sb.append("Meilleur cumul : ").append(nomComplet(meilleur))
              .append(" (").append(km(num(meilleur.get("cumul_m")))).append(" km).\n");
        }

        List<Object> sur = asList(ch.get("surcharge"));
        int nbSur = num(ch.get("nb_surcharge"));
        if (nbSur > 0) {
            sb.append("Surcharge (au-dessus du plafond ACWR) : ").append(nbSur).append(" joueur(s)");
            sb.append(detailCharge(sur, "plafond_m")).append(".\n");
        }
        List<Object> sous = asList(ch.get("souscharge"));
        int nbSous = num(ch.get("nb_souscharge"));
        if (nbSous > 0) {
            sb.append("Sous-charge (sous la cible minimale) : ").append(nbSous).append(" joueur(s)");
            sb.append(detailCharge(sous, "cible_min_m")).append(".\n");
        }
        if (nbSur == 0 && nbSous == 0) {
            sb.append("Charge : aucun joueur en surcharge ni en sous-charge marquée.\n");
        }
        return sb.toString();
    }

    /** « — Nom (X.X km vs seuil Y.Y) » pour au plus 3 joueurs. */
    private String detailCharge(List<Object> joueurs, String cleSeuil) {
        StringJoiner sj = new StringJoiner(", ", " — ", "");
        sj.setEmptyValue("");
        for (Object o : joueurs) {
            Map<String, Object> j = asMap(o);
            sj.add(nomComplet(j) + " (" + km(num(j.get("cumul_m"))) + " km, seuil " + km(num(j.get(cleSeuil))) + ")");
        }
        return sj.toString();
    }

    // ── Gabarit de repli (aucun LLM) : mêmes chiffres, phrasé fixe en rotation ──

    private static final String[] OUVERTURES = {
            "Point de la semaine.",
            "Note du prépa.",
            "État de l'équipe cette semaine.",
            "Bilan hebdomadaire.",
            "Synthèse de la semaine.",
    };

    private String gabarit(Map<String, Object> b) {
        Map<String, Object> effectif = asMap(b.get("effectif"));
        int nbJoueurs = num(effectif.get("nb_joueurs"));
        String ouverture = OUVERTURES[LocalDate.now().getDayOfYear() % OUVERTURES.length];
        if (nbJoueurs == 0) {
            return ouverture + " Pas encore de données de charge exploitables cette semaine "
                    + "(effectif ou GPS manquants) — le briefing sera disponible dès les premiers relevés.";
        }

        Map<String, Object> oh = asMap(b.get("objectif_hebdo"));
        Map<String, Object> ch = asMap(b.get("charge_semaine"));

        StringJoiner txt = new StringJoiner(" ");
        txt.add(ouverture);

        int nbAtteint = num(oh.get("nb_atteint"));
        int nbConcernes = num(oh.get("nb_concernes"));
        String source = str(oh.get("source"));
        Integer objManuel = numOrNull(oh.get("objectif_manuel_m"));
        Integer suggestion = numOrNull(oh.get("suggestion_moyenne_m"));
        if (nbConcernes > 0) {
            String cible;
            if ("MANUEL".equals(source) && objManuel != null) cible = "l'objectif de " + km(objManuel) + " km/joueur";
            else if ("INTELLIGENT".equals(source) && suggestion != null) cible = "la cible intelligente (~" + km(suggestion) + " km/joueur)";
            else cible = "l'objectif";
            String phrase = nbAtteint + " joueur(s) sur " + nbConcernes + " ont atteint " + cible + " cette semaine.";
            Integer resteMoyen = numOrNull(oh.get("reste_moyen_m"));
            if (resteMoyen != null && resteMoyen > 0) phrase += " Il reste en moyenne " + km(resteMoyen) + " km aux autres.";
            txt.add(phrase);
        } else {
            txt.add("Objectif hebdomadaire non exploitable pour l'instant (charge chronique insuffisante).");
        }

        Map<String, Object> meilleur = asMap(oh.get("meilleur"));
        if (!meilleur.isEmpty()) {
            txt.add("Meilleur cumul : " + nomComplet(meilleur) + " (" + km(num(meilleur.get("cumul_m"))) + " km).");
        }

        int nbSur = num(ch.get("nb_surcharge"));
        int nbSous = num(ch.get("nb_souscharge"));
        if (nbSur > 0 || nbSous > 0) {
            StringJoiner alerte = new StringJoiner(" et ", "À surveiller : ", ".");
            if (nbSur > 0) alerte.add(nbSur + " en surcharge" + premiers(asList(ch.get("surcharge"))));
            if (nbSous > 0) alerte.add(nbSous + " en sous-charge" + premiers(asList(ch.get("souscharge"))));
            txt.add(alerte.toString());
        } else {
            txt.add("Charge globalement maîtrisée, aucun joueur en surcharge ni en sous-charge marquée.");
        }
        return txt.toString();
    }

    /** « (Nom, Nom) » pour au plus 2 joueurs, sinon chaîne vide. */
    private String premiers(List<Object> joueurs) {
        StringJoiner sj = new StringJoiner(", ", " (", ")");
        sj.setEmptyValue("");
        int n = 0;
        for (Object o : joueurs) {
            if (n++ >= 2) break;
            sj.add(nomComplet(asMap(o)));
        }
        return sj.toString();
    }

    // ── Utilitaires ──

    private String nomComplet(Map<String, Object> j) {
        String direct = str(j.get("nom"));
        // Le bundle charge_semaine fournit déjà "nom" = « Prénom Nom » ; l'objet meilleur a prénom+nom séparés.
        String prenom = str(j.get("prenom"));
        if (!prenom.isBlank()) return (prenom + " " + direct).trim();
        return direct.isBlank() ? "un joueur" : direct;
    }

    /** Mètres → km avec une décimale (virgule française). */
    private String km(int metres) {
        return String.format(Locale.FRANCE, "%.1f", metres / 1000.0);
    }

    private UUID clubCourant() {
        Utilisateur u = currentUser.current();
        if (u.getRole() == Role.SUPER_ADMIN) {
            ContexteActif ctx = ContexteActifHolder.get();
            return ctx != null ? ctx.clubId() : null;
        }
        return u.getClubId();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object o) {
        return o instanceof List ? (List<Object>) o : List.of();
    }

    private static int num(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    private static Integer numOrNull(Object o) {
        return o instanceof Number n ? n.intValue() : null;
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }
}
