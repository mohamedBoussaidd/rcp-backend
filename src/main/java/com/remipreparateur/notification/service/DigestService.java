package com.remipreparateur.notification.service;

import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.medical.wellness.entity.WellnessQuotidien;
import com.remipreparateur.medical.wellness.repository.WellnessQuotidienRepository;
import com.remipreparateur.notification.entity.NotifConfigEquipe;
import com.remipreparateur.notification.entity.Priorite;
import com.remipreparateur.notification.entity.TypeNotification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Construit et envoie le digest « à surveiller » d'une équipe : agrège les dépassements de
 * seuils <b>par joueur</b> (charge ACWR + readiness depuis l'analytics Python ; items wellness
 * et poids depuis la base) pour un message scannable — une ligne par joueur, triée par gravité,
 * avec un teaser en tête (nombre à surveiller + complétion wellness). Le bloc poids n'est inclus
 * que les jours de pesée configurés. N'envoie rien s'il n'y a ni signalement ni complétion basse.
 */
@Service
public class DigestService {

    /** Nombre max de joueurs listés dans le corps de la notification (détail complet sur le dashboard). */
    private static final int MAX_LIGNES = 6;

    private final RestTemplate restTemplate;
    private final JoueurRepository joueurRepository;
    private final WellnessQuotidienRepository wellnessRepository;
    private final NotificationDispatcher dispatcher;

    @Value("${python.api.url}")
    private String pythonApiUrl;

    public DigestService(RestTemplate restTemplate, JoueurRepository joueurRepository,
                         WellnessQuotidienRepository wellnessRepository,
                         NotificationDispatcher dispatcher) {
        this.restTemplate = restTemplate;
        this.joueurRepository = joueurRepository;
        this.wellnessRepository = wellnessRepository;
        this.dispatcher = dispatcher;
    }

    /** Agrégat des signaux d'un joueur pour le digest. */
    private static final class Agg {
        final String nom;
        int gravite = 0;
        final List<String> soucis = new ArrayList<>();
        Agg(String nom) { this.nom = nom; }
    }

    /** Calcule les signalements de l'équipe et envoie le digest au staff. Renvoie le nb de joueurs à surveiller. */
    public int genererEtEnvoyer(NotifConfigEquipe cfg, String moment) {
        UUID equipeId = cfg.getEquipeId();
        List<Joueur> joueurs = joueurRepository.findByEquipeIdIn(List.of(equipeId)).stream()
                .filter(j -> !"inactif".equalsIgnoreCase(j.getStatut()))
                .toList();
        if (joueurs.isEmpty()) return 0;

        Map<UUID, Agg> parJoueur = new LinkedHashMap<>();
        joueurs.forEach(j -> parJoueur.put(j.getId(), new Agg(nom(j))));

        remplirAnalytics(cfg, joueurs, parJoueur);
        remplirWellness(cfg, joueurs, parJoueur);
        if (CadenceJours.actifLe(cfg.getDigestPoidsJours(), LocalDate.now())) {
            remplirPoids(cfg, joueurs, parJoueur);
        }

        List<Agg> aSurveiller = parJoueur.values().stream()
                .filter(a -> !a.soucis.isEmpty())
                .sorted(Comparator.comparingInt((Agg a) -> a.gravite).reversed())
                .toList();

        // Complétion wellness du jour (toujours affichée dans le teaser).
        LocalDate today = LocalDate.now();
        long saisis = joueurs.stream()
                .filter(j -> wellnessRepository.findByJoueurIdAndDate(j.getId(), today).isPresent())
                .count();
        int total = joueurs.size();
        int pct = (int) Math.round(100.0 * saisis / total);
        boolean completionBasse = pct < cfg.getSeuilCompletionMin();

        if (aSurveiller.isEmpty() && !completionBasse) return 0; // rien à signaler → pas de bruit

        int n = aSurveiller.size();
        String titre = "À surveiller — " + moment + " (" + n + ")";

        StringBuilder corps = new StringBuilder();
        corps.append(n == 0 ? "Aucun joueur à surveiller"
                : (n + (n > 1 ? " joueurs à surveiller" : " joueur à surveiller")));
        corps.append(" · complétion wellness ").append(pct).append("% (").append(saisis).append("/").append(total).append(")");
        int i = 0;
        for (Agg a : aSurveiller) {
            if (i++ >= MAX_LIGNES) break;
            corps.append("\n").append(a.gravite >= 3 ? "🔴 " : "🟠 ")
                    .append(a.nom).append(" — ").append(String.join(" · ", a.soucis));
        }
        if (n > MAX_LIGNES) {
            corps.append("\n… et ").append(n - MAX_LIGNES).append(" autre").append(n - MAX_LIGNES > 1 ? "s" : "");
        }

        dispatcher.versStaff(equipeId, TypeNotification.DIGEST, titre, corps.toString(),
                "/dashboard", null, Priorite.NORMALE);
        return n;
    }

    // ── Charge (ACWR) + readiness, depuis l'analytics Python ──
    private void remplirAnalytics(NotifConfigEquipe cfg, List<Joueur> joueurs, Map<UUID, Agg> parJoueur) {
        try {
            Object res = restTemplate.getForObject(pythonApiUrl + "/api/predictions/equipe", List.class);
            if (!(res instanceof List<?> list)) return;
            double acwrHaut = cfg.getSeuilAcwrHaut().doubleValue();
            double acwrBas = cfg.getSeuilAcwrBas().doubleValue();
            double readyMin = cfg.getSeuilReadinessMin().doubleValue();
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m)) continue;
                UUID jid = parseUuid(m.get("joueur_id"));
                Agg a = jid == null ? null : parJoueur.get(jid);
                if (a == null) continue;
                Double acwr = toDouble(m.get("acwr"));
                if (acwr != null && acwr >= acwrHaut) { a.soucis.add("ACWR " + arrondi(acwr)); a.gravite += 3; }
                else if (acwr != null && acwr <= acwrBas) { a.soucis.add("sous-charge (ACWR " + arrondi(acwr) + ")"); a.gravite += 1; }
                Double ready = toDouble(m.get("readiness"));
                if (ready != null && ready < readyMin) { a.soucis.add("readiness " + arrondi(ready)); a.gravite += 2; }
            }
        } catch (Exception ignore) {
            // analytics indisponible → on n'ajoute pas ces signaux
        }
    }

    // ── Items wellness du jour (regroupés en un seul libellé par joueur) ──
    private void remplirWellness(NotifConfigEquipe cfg, List<Joueur> joueurs, Map<UUID, Agg> parJoueur) {
        LocalDate today = LocalDate.now();
        for (Joueur j : joueurs) {
            WellnessQuotidien w = wellnessRepository.findByJoueurIdAndDate(j.getId(), today).orElse(null);
            if (w == null) continue;
            List<String> items = new ArrayList<>();
            if (w.getFatigue() != null && w.getFatigue() >= cfg.getSeuilWellnessFatigue()) items.add("fatigue");
            if (w.getDouleur() != null && w.getDouleur() >= cfg.getSeuilWellnessDouleur()) items.add("douleurs");
            if (w.getStress() != null && w.getStress() >= cfg.getSeuilWellnessStress()) items.add("stress");
            if (w.getSommeil() != null && w.getSommeil() >= cfg.getSeuilWellnessSommeil()) items.add("sommeil");
            if (w.getHumeur() != null && w.getHumeur() >= cfg.getSeuilWellnessHumeur()) items.add("humeur");
            if (!items.isEmpty()) {
                Agg a = parJoueur.get(j.getId());
                if (a != null) { a.soucis.add("ressenti : " + String.join(", ", items)); a.gravite += items.size(); }
            }
        }
    }

    // ── Écart de poids vs poids de forme cible (jours de pesée uniquement) ──
    private void remplirPoids(NotifConfigEquipe cfg, List<Joueur> joueurs, Map<UUID, Agg> parJoueur) {
        double seuil = cfg.getSeuilPoidsMoyen().doubleValue();
        for (Joueur j : joueurs) {
            BigDecimal actuel = j.getPoidsActuel();
            BigDecimal cible = j.getPoidsFormeCible();
            if (actuel == null || cible == null) continue;
            double ecart = actuel.subtract(cible).doubleValue();
            if (Math.abs(ecart) >= seuil) {
                Agg a = parJoueur.get(j.getId());
                if (a != null) { a.soucis.add("poids " + (ecart > 0 ? "+" : "") + arrondi(ecart) + " kg"); a.gravite += 1; }
            }
        }
    }

    // ── Helpers ──
    private String nom(Joueur j) { return (j.getPrenom() + " " + j.getNom()).trim(); }

    private static String arrondi(double d) { return String.valueOf(Math.round(d * 10.0) / 10.0); }

    private static Double toDouble(Object o) {
        return (o instanceof Number n) ? n.doubleValue() : null;
    }

    private static UUID parseUuid(Object o) {
        try { return o == null ? null : UUID.fromString(String.valueOf(o)); }
        catch (IllegalArgumentException e) { return null; }
    }
}
