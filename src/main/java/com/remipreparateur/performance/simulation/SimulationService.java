package com.remipreparateur.performance.simulation;

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
 * Carte IA « simulation — et si… ». À partir d'une séance HYPOTHÉTIQUE (type + durée), Python
 * projette la distance attendue de chaque joueur (baseline m/min du même type de séance) et
 * recalcule son ACWR en ajoutant cette distance à la charge aiguë. Ce service expose le résultat
 * structuré (aucun coût IA) et sa mise en mots via le {@link CarteIaService} (LLM si disponible,
 * sinon gabarit local).
 *
 * <p>Le scénario « une séance » est le premier d'une famille : d'autres viendront (semaine complète,
 * retour de blessure, ajout d'un match) sous le même add-on {@code assistant_simulation}.
 *
 * <p>Rien n'est écrit : la simulation est une projection en lecture seule.
 */
@Service
public class SimulationService {

    /** Identifiant de la carte : quota + toggle LLM + prompt (voir {@link ParametreIaService}). */
    public static final String FEATURE = "simulation_prepa";

    private final PredictionService predictionService;
    private final CarteIaService carteIa;
    private final CurrentUserProvider currentUser;

    public SimulationService(PredictionService predictionService, CarteIaService carteIa,
                             CurrentUserProvider currentUser) {
        this.predictionService = predictionService;
        this.carteIa = carteIa;
        this.currentUser = currentUser;
    }

    /** Résultat structuré de la simulation (aucun appel LLM, donc gratuit et illimité). */
    public Object simuler(UUID typeSeanceId, int dureeMinutes) {
        return predictionService.postSimulationSeance(typeSeanceId, dureeMinutes);
    }

    /** Mise en mots de la simulation (LLM ou gabarit). Consomme le quota de la carte. */
    public CarteIaService.TexteCarte genererNote(UUID typeSeanceId, int dureeMinutes) {
        Map<String, Object> s = asMap(predictionService.postSimulationSeance(typeSeanceId, dureeMinutes));
        return carteIa.produire(clubCourant(), FEATURE, ParametreIaService.CLE_PROMPT_SIMULATION_PREPA,
                construireIndicateurs(s), () -> gabarit(s));
    }

    // ── Bloc d'indicateurs factuels envoyé au LLM ──

    private String construireIndicateurs(Map<String, Object> s) {
        Map<String, Object> seance = asMap(s.get("seance"));
        Map<String, Object> syn = asMap(s.get("synthese"));
        int nbEvalues = num(syn.get("nb_evalues"));

        StringBuilder sb = new StringBuilder();
        sb.append("CONTEXTE : simulation d'une séance À VENIR (elle n'a pas eu lieu).\n");
        sb.append("Séance simulée : ").append(libelleSeance(seance)).append(".\n");
        if (nbEvalues == 0) {
            sb.append("Aucun joueur évaluable : pas d'historique GPS exploitable pour projeter une distance.");
            return sb.toString();
        }

        sb.append("Effectif projeté : ").append(nbEvalues).append(" joueurs");
        int sansBaseline = num(syn.get("nb_sans_baseline"));
        if (sansBaseline > 0) sb.append(" (").append(sansBaseline).append(" sans historique, non évalués)");
        sb.append(".\n");

        Object kmMoyen = syn.get("km_attendu_moyen");
        if (kmMoyen instanceof Number n) {
            sb.append("Distance attendue moyenne : ").append(dec(n.doubleValue())).append(" km/joueur.\n");
        }

        int surAvant = num(syn.get("nb_surcharge_avant"));
        int surApres = num(syn.get("nb_surcharge_apres"));
        int bascule = num(syn.get("nb_bascule"));
        sb.append("Joueurs au-dessus du plafond ACWR : ").append(surAvant)
          .append(" actuellement, ").append(surApres).append(" après cette séance");
        sb.append(bascule > 0 ? " (soit " + bascule + " qui bascule(nt)).\n" : " (aucune bascule).\n");

        List<Object> joueurs = asList(s.get("joueurs"));
        StringJoiner bascules = new StringJoiner(", ");
        StringJoiner hauts = new StringJoiner(", ");
        for (Object o : joueurs) {
            Map<String, Object> j = asMap(o);
            if (j.get("acwr_apres") == null) continue;
            String desc = nomComplet(j) + " (ACWR " + str(j.get("acwr_avant")) + " → " + str(j.get("acwr_apres"))
                    + ", +" + dec(dbl(j.get("km_attendu"))) + " km)";
            if (Boolean.TRUE.equals(j.get("bascule"))) {
                if (bascules.length() < 200) bascules.add(desc);
            } else if ("SURCHARGE".equals(str(j.get("zone_apres")))) {
                if (hauts.length() < 200) hauts.add(desc);
            }
        }
        if (bascules.length() > 0) sb.append("Basculeraient en surcharge : ").append(bascules).append(".\n");
        if (hauts.length() > 0) sb.append("Déjà en surcharge et le restent : ").append(hauts).append(".\n");

        int peuFiable = num(syn.get("nb_peu_fiable"));
        if (peuFiable > 0) {
            sb.append("Fiabilité : ").append(peuFiable)
              .append(" joueur(s) avec moins de 3 séances de référence — projection à prendre avec prudence.\n");
        }
        return sb.toString();
    }

    // ── Gabarit de repli (aucun LLM) : mêmes chiffres, phrasé fixe en rotation ──

    private static final String[] OUVERTURES = {
            "Projection de la séance.", "Si cette séance a lieu.", "Simulation de charge.",
            "Impact estimé de la séance.", "Ce que donnerait cette séance.",
    };

    private String gabarit(Map<String, Object> s) {
        Map<String, Object> seance = asMap(s.get("seance"));
        Map<String, Object> syn = asMap(s.get("synthese"));
        String ouverture = OUVERTURES[LocalDate.now().getDayOfYear() % OUVERTURES.length];
        int nbEvalues = num(syn.get("nb_evalues"));
        if (nbEvalues == 0) {
            return ouverture + " Impossible de projeter cette séance : aucun joueur n'a d'historique GPS "
                    + "exploitable sur ce type de séance.";
        }

        StringJoiner txt = new StringJoiner(" ");
        txt.add(ouverture);
        Object kmMoyen = syn.get("km_attendu_moyen");
        String base = libelleSeance(seance);
        txt.add(kmMoyen instanceof Number n
                ? "Sur une " + base + ", on attend en moyenne " + dec(n.doubleValue()) + " km par joueur."
                : "Séance simulée : " + base + ".");

        int bascule = num(syn.get("nb_bascule"));
        int surApres = num(syn.get("nb_surcharge_apres"));
        if (bascule > 0) {
            StringJoiner noms = new StringJoiner(", ", " (", ")");
            noms.setEmptyValue("");
            int n = 0;
            for (Object o : asList(s.get("joueurs"))) {
                Map<String, Object> j = asMap(o);
                if (!Boolean.TRUE.equals(j.get("bascule"))) continue;
                if (n++ >= 3) break;
                noms.add(nomComplet(j));
            }
            txt.add(bascule + " joueur(s) passeraient au-dessus du plafond ACWR" + noms + ".");
            txt.add("À envisager : alléger le volume, décaler la séance ou individualiser pour eux.");
        } else if (surApres > 0) {
            txt.add("Aucune nouvelle bascule, mais " + surApres + " joueur(s) restent au-dessus du plafond.");
        } else {
            txt.add("Aucun joueur ne passerait au-dessus du plafond ACWR : la séance est absorbable en l'état.");
        }

        int peuFiable = num(syn.get("nb_peu_fiable"));
        if (peuFiable > 0) {
            txt.add("Projection à prendre avec prudence pour " + peuFiable
                    + " joueur(s) : moins de 3 séances de référence.");
        }
        return txt.toString();
    }

    // ── Utilitaires ──

    private String libelleSeance(Map<String, Object> seance) {
        String type = str(seance.get("type_libelle"));
        int duree = num(seance.get("duree_minutes"));
        String base = type.isBlank() ? "séance (tous types confondus)" : "séance « " + type + " »";
        return duree > 0 ? base + " de " + duree + " min" : base;
    }

    private String nomComplet(Map<String, Object> j) {
        String nom = str(j.get("nom"));
        String prenom = str(j.get("prenom"));
        String complet = (prenom + " " + nom).trim();
        return complet.isBlank() ? "un joueur" : complet;
    }

    /** Nombre à une décimale, virgule française. */
    private String dec(double v) {
        return String.format(Locale.FRANCE, "%.1f", v);
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
    private static Map<String, Object> asMap(Object o) { return o instanceof Map ? (Map<String, Object>) o : Map.of(); }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object o) { return o instanceof List ? (List<Object>) o : List.of(); }

    private static int num(Object o) { return o instanceof Number n ? n.intValue() : 0; }

    private static double dbl(Object o) { return o instanceof Number n ? n.doubleValue() : 0.0; }

    private static String str(Object o) { return o == null ? "" : o.toString(); }
}
