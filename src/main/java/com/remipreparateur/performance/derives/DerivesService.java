package com.remipreparateur.performance.derives;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * Carte IA « dérives & surveillance » : expose les dérives DÉJÀ calculées (3 axes séparés — volume,
 * haute intensité, ressenti) pour l'affichage structuré (web/PWA), et met en mots une synthèse de
 * surveillance via le {@link CarteIaService} (LLM si disponible, sinon gabarit local).
 */
@Service
public class DerivesService {

    public static final String FEATURE = "derives_prepa";

    private final PredictionService predictionService;
    private final CarteIaService carteIa;
    private final CurrentUserProvider currentUser;

    public DerivesService(PredictionService predictionService, CarteIaService carteIa,
                          CurrentUserProvider currentUser) {
        this.predictionService = predictionService;
        this.carteIa = carteIa;
        this.currentUser = currentUser;
    }

    /** Bundle structuré des dérives (axes + joueurs), consommé tel quel par la carte (aucun coût IA). */
    public Object indicateurs() {
        return predictionService.getDerives();
    }

    /** Synthèse textuelle de surveillance (LLM ou gabarit). */
    public CarteIaService.TexteCarte genererNote() {
        Map<String, Object> d = asMap(predictionService.getDerives());
        UUID clubId = clubCourant();
        return carteIa.produire(clubId, FEATURE, ParametreIaService.CLE_PROMPT_DERIVES_PREPA,
                construireIndicateurs(d), () -> gabarit(d));
    }

    /**
     * Corps concis d'alerte de surveillance pour une équipe (appelé par le scheduler, hors HTTP) :
     * résume les axes en dérive, ou {@link Optional#empty()} si rien ne dépasse le seuil. Pas d'appel
     * LLM ici (une notification texte suffit ; la synthèse rédigée reste sur la carte à la demande).
     */
    public Optional<String> alertePourEquipe(UUID equipeId) {
        Map<String, Object> d = asMap(predictionService.getDerivesEquipe(equipeId));
        List<String> bouts = new ArrayList<>();
        for (Object o : asList(d.get("axes"))) {
            Map<String, Object> a = asMap(o);
            int nh = num(a.get("nb_hausse")), nb = num(a.get("nb_baisse"));
            if (nh == 0 && nb == 0) continue;
            StringBuilder p = new StringBuilder(str(a.get("libelle"))).append(" : ");
            if (nh > 0) p.append(nh).append(" en hausse").append(premiers(asList(a.get("hausse"))));
            if (nh > 0 && nb > 0) p.append(", ");
            if (nb > 0) p.append(nb).append(" en baisse").append(premiers(asList(a.get("baisse"))));
            bouts.add(p.toString());
        }
        return bouts.isEmpty() ? Optional.empty() : Optional.of(String.join(" · ", bouts));
    }

    // ── Bloc d'indicateurs envoyé au LLM ──

    private String construireIndicateurs(Map<String, Object> d) {
        List<Object> axes = asList(d.get("axes"));
        int nb = num(asMap(d.get("effectif")).get("nb_joueurs"));
        if (nb == 0 || axes.isEmpty()) {
            return "CONTEXTE : surveillance des dérives de charge de l'effectif (4 semaines).\n"
                    + "Aucune donnée exploitable sur la période.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("CONTEXTE : surveillance des dérives lentes de l'effectif sur 4 semaines (")
          .append(nb).append(" joueurs), comparaison 14 derniers jours vs 14 précédents.\n");
        for (Object o : axes) {
            Map<String, Object> a = asMap(o);
            sb.append("AXE « ").append(str(a.get("libelle"))).append(" » (hausse = ")
              .append(str(a.get("sens_hausse"))).append(") : ");
            int nh = num(a.get("nb_hausse")), nb2 = num(a.get("nb_baisse"));
            if (nh == 0 && nb2 == 0) {
                sb.append("aucune dérive marquée.\n");
                continue;
            }
            if (nh > 0) sb.append(nh).append(" en hausse").append(detail(asList(a.get("hausse")))).append(". ");
            if (nb2 > 0) sb.append(nb2).append(" en baisse").append(detail(asList(a.get("baisse")))).append(".");
            sb.append("\n");
        }
        return sb.toString();
    }

    private String detail(List<Object> joueurs) {
        StringJoiner sj = new StringJoiner(", ", " (", ")");
        sj.setEmptyValue("");
        int n = 0;
        for (Object o : joueurs) {
            if (n++ >= 3) break;
            Map<String, Object> j = asMap(o);
            Double drift = numDouble(j.get("drift_pct"));
            sj.add(str(j.get("nom")) + (drift != null ? " " + (drift > 0 ? "+" : "") + Math.round(drift) + "%" : ""));
        }
        return sj.toString();
    }

    // ── Gabarit de repli ──

    private static final String[] OUVERTURES = {
            "Surveillance de l'effectif.", "Dérives à l'œil.", "Point de surveillance.",
            "Tendances sur 4 semaines.", "Radar de charge.",
    };

    private String gabarit(Map<String, Object> d) {
        List<Object> axes = asList(d.get("axes"));
        int nb = num(asMap(d.get("effectif")).get("nb_joueurs"));
        String ouverture = OUVERTURES[LocalDate.now().getDayOfYear() % OUVERTURES.length];
        if (nb == 0 || axes.isEmpty()) {
            return ouverture + " Pas assez de données sur les 4 dernières semaines pour repérer des dérives.";
        }
        StringJoiner txt = new StringJoiner(" ");
        txt.add(ouverture);
        boolean rien = true;
        for (Object o : axes) {
            Map<String, Object> a = asMap(o);
            int nh = num(a.get("nb_hausse")), nb2 = num(a.get("nb_baisse"));
            if (nh == 0 && nb2 == 0) continue;
            rien = false;
            StringBuilder p = new StringBuilder(str(a.get("libelle"))).append(" : ");
            if (nh > 0) p.append(nh).append(" en hausse").append(premiers(asList(a.get("hausse"))));
            if (nh > 0 && nb2 > 0) p.append(", ");
            if (nb2 > 0) p.append(nb2).append(" en baisse").append(premiers(asList(a.get("baisse"))));
            p.append(".");
            txt.add(p.toString());
        }
        if (rien) txt.add("Aucune dérive marquée sur les 4 dernières semaines : effectif stable.");
        return txt.toString();
    }

    private String premiers(List<Object> joueurs) {
        StringJoiner sj = new StringJoiner(", ", " (", ")");
        sj.setEmptyValue("");
        int n = 0;
        for (Object o : joueurs) {
            if (n++ >= 2) break;
            sj.add(str(asMap(o).get("nom")));
        }
        return sj.toString();
    }

    // ── Utilitaires ──

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

    private static Double numDouble(Object o) { return o instanceof Number n ? n.doubleValue() : null; }

    private static String str(Object o) { return o == null ? "" : o.toString(); }
}
