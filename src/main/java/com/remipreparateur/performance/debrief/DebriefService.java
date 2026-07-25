package com.remipreparateur.performance.debrief;

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
 * Carte IA « debrief de séance » : à partir du RAPPORT déjà calculé d'une séance réalisée (prévu vs
 * réalisé, objectif de charge, écarts perso), met en forme un bloc d'indicateurs factuels puis délègue
 * au {@link CarteIaService} (LLM si disponible, sinon gabarit local). Aucune donnée brute au LLM.
 */
@Service
public class DebriefService {

    public static final String FEATURE = "debrief_seance";

    private final PredictionService predictionService;
    private final CarteIaService carteIa;
    private final CurrentUserProvider currentUser;

    public DebriefService(PredictionService predictionService, CarteIaService carteIa,
                          CurrentUserProvider currentUser) {
        this.predictionService = predictionService;
        this.carteIa = carteIa;
        this.currentUser = currentUser;
    }

    public CarteIaService.TexteCarte generer(UUID seanceId) {
        Map<String, Object> r = asMap(predictionService.getRapportSeance(seanceId));
        UUID clubId = clubCourant();
        String indicateurs = construireIndicateurs(r);
        return carteIa.produire(clubId, FEATURE, ParametreIaService.CLE_PROMPT_DEBRIEF_SEANCE,
                indicateurs, () -> gabarit(r));
    }

    // ── Agrégats communs (indicateurs + gabarit) ──

    private record Bilan(int nbJoueurs, int nbAtteint, int nbConcernes, long prevuTotal, long realTotal,
                         boolean prevuDispo, List<Map<String, Object>> sur, List<Map<String, Object>> sous) {}

    private Bilan agreger(Map<String, Object> r) {
        List<Object> lignes = asList(r.get("lignes"));
        int nbAtteint = 0, nbConcernes = 0;
        long prevu = 0, real = 0;
        boolean prevuDispo = false;
        List<Map<String, Object>> sur = new java.util.ArrayList<>();
        List<Map<String, Object>> sous = new java.util.ArrayList<>();
        for (Object o : lignes) {
            Map<String, Object> j = asMap(o);
            Integer dist = numOrNull(j.get("distance_reelle"));
            Integer objSeance = numOrNull(j.get("objectif_seance_m"));
            if (dist != null) real += dist;
            if (objSeance != null) { prevu += objSeance; prevuDispo = true; }
            Object atteint = j.get("atteint_objectif_seance");
            if (atteint instanceof Boolean b) { nbConcernes++; if (b) nbAtteint++; }
            String statut = str(j.get("statut"));
            if ("SUR_NORME".equals(statut)) sur.add(j);
            else if ("SOUS_NORME".equals(statut)) sous.add(j);
        }
        sur.sort((a, b) -> Double.compare(dbl(b.get("delta_pct")), dbl(a.get("delta_pct"))));
        sous.sort((a, b) -> Double.compare(dbl(a.get("delta_pct")), dbl(b.get("delta_pct"))));
        return new Bilan(asList(r.get("lignes")).size(), nbAtteint, nbConcernes, prevu, real, prevuDispo, sur, sous);
    }

    // ── Bloc d'indicateurs envoyé au LLM ──

    private String construireIndicateurs(Map<String, Object> r) {
        Bilan b = agreger(r);
        String type = str(r.get("type_libelle"));
        String date = str(r.get("date"));
        if (b.nbJoueurs() == 0) {
            return "CONTEXTE : debrief d'une séance réalisée (" + type + " du " + date + ").\n"
                    + "Aucune donnée GPS exploitable sur cette séance.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("CONTEXTE : debrief d'une séance réalisée.\n");
        sb.append("Séance : ").append(type).append(" du ").append(date)
          .append(", ").append(b.nbJoueurs()).append(" joueurs avec données GPS.\n");
        if (b.nbConcernes() > 0) {
            sb.append("Objectif de charge (équipe) : ").append(b.nbAtteint()).append(" joueurs sur ")
              .append(b.nbConcernes()).append(" ont atteint leur objectif de volume de la séance.\n");
        } else {
            sb.append("Objectif de charge : non défini pour cette séance (pas de cible de volume).\n");
        }
        if (b.prevuDispo()) {
            sb.append("Volume d'équipe : ").append(km(b.realTotal())).append(" km réalisés vs ")
              .append(km(b.prevuTotal())).append(" km attendus.\n");
        }
        if (!b.sur().isEmpty()) {
            sb.append("Nettement au-dessus de leur attendu perso : ").append(b.sur().size())
              .append(detail(b.sur())).append(".\n");
        }
        if (!b.sous().isEmpty()) {
            sb.append("Nettement en-dessous de leur attendu perso : ").append(b.sous().size())
              .append(detail(b.sous())).append(".\n");
        }
        if (b.sur().isEmpty() && b.sous().isEmpty()) {
            sb.append("Écarts individuels : tout le monde dans sa norme attendue.\n");
        }
        return sb.toString();
    }

    private String detail(List<Map<String, Object>> joueurs) {
        StringJoiner sj = new StringJoiner(", ", " — ", "");
        sj.setEmptyValue("");
        int n = 0;
        for (Map<String, Object> j : joueurs) {
            if (n++ >= 3) break;
            Double d = numDouble(j.get("delta_pct"));
            sj.add(nomComplet(j) + (d != null ? " (" + (d > 0 ? "+" : "") + arrondi(d) + "%)" : ""));
        }
        return sj.toString();
    }

    // ── Gabarit de repli ──

    private static final String[] OUVERTURES = {
            "Debrief de la séance.", "Bilan de la séance.", "Retour sur la séance.",
            "À chaud sur la séance.", "Synthèse de la séance.",
    };

    private String gabarit(Map<String, Object> r) {
        String type = str(r.get("type_libelle"));
        String date = str(r.get("date"));
        String ouverture = OUVERTURES[LocalDate.now().getDayOfYear() % OUVERTURES.length];
        Bilan b = agreger(r);
        if (b.nbJoueurs() == 0) {
            return ouverture + " " + type + " du " + date + " : aucune donnée GPS exploitable pour cette séance.";
        }
        StringJoiner txt = new StringJoiner(" ");
        txt.add(ouverture);
        txt.add(type + " du " + date + ", " + b.nbJoueurs() + " joueurs suivis.");
        if (b.nbConcernes() > 0) {
            String phrase = b.nbAtteint() + " joueur(s) sur " + b.nbConcernes() + " ont atteint leur objectif de volume";
            if (b.prevuDispo()) phrase += " (" + km(b.realTotal()) + " km réalisés vs " + km(b.prevuTotal()) + " km attendus)";
            txt.add(phrase + ".");
        } else if (b.prevuDispo()) {
            txt.add("Volume d'équipe : " + km(b.realTotal()) + " km réalisés vs " + km(b.prevuTotal()) + " km attendus.");
        }
        if (!b.sur().isEmpty() || !b.sous().isEmpty()) {
            StringJoiner ecarts = new StringJoiner(" ; ", "Écarts : ", ".");
            if (!b.sur().isEmpty()) ecarts.add(b.sur().size() + " au-dessus" + premiers(b.sur()));
            if (!b.sous().isEmpty()) ecarts.add(b.sous().size() + " en-dessous" + premiers(b.sous()));
            txt.add(ecarts.toString());
        } else {
            txt.add("Charges individuelles conformes à l'attendu.");
        }
        return txt.toString();
    }

    private String premiers(List<Map<String, Object>> joueurs) {
        StringJoiner sj = new StringJoiner(", ", " (", ")");
        sj.setEmptyValue("");
        int n = 0;
        for (Map<String, Object> j : joueurs) {
            if (n++ >= 2) break;
            sj.add(nomComplet(j));
        }
        return sj.toString();
    }

    // ── Utilitaires ──

    private String nomComplet(Map<String, Object> j) {
        String prenom = str(j.get("prenom"));
        String nom = str(j.get("nom"));
        String complet = (prenom + " " + nom).trim();
        return complet.isBlank() ? "un joueur" : complet;
    }

    private String km(long metres) { return String.format(Locale.FRANCE, "%.1f", metres / 1000.0); }

    private long arrondi(double d) { return Math.round(d); }

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

    private static Integer numOrNull(Object o) { return o instanceof Number n ? n.intValue() : null; }

    private static Double numDouble(Object o) { return o instanceof Number n ? n.doubleValue() : null; }

    private static double dbl(Object o) { return o instanceof Number n ? n.doubleValue() : 0.0; }

    private static String str(Object o) { return o == null ? "" : o.toString(); }
}
