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
 * seuils (charge ACWR + readiness depuis l'analytics Python ; items wellness, poids et taux
 * de complétion depuis la base) et diffuse UN résumé au staff (routage DIGEST). N'envoie rien
 * s'il n'y a aucun signalement. Tous les accès externes sont protégés (best-effort).
 */
@Service
public class DigestService {

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

    /** Calcule les signalements de l'équipe et envoie le digest au staff. Renvoie le nb de signalements. */
    public int genererEtEnvoyer(NotifConfigEquipe cfg, String moment) {
        UUID equipeId = cfg.getEquipeId();
        List<Joueur> joueurs = joueurRepository.findByEquipeIdIn(List.of(equipeId)).stream()
                .filter(j -> !"inactif".equalsIgnoreCase(j.getStatut()))
                .toList();
        if (joueurs.isEmpty()) return 0;

        List<String> lignes = new ArrayList<>();
        lignes.addAll(signauxAnalytics(cfg, joueurs));
        lignes.addAll(signauxWellness(cfg, joueurs));
        lignes.addAll(signauxPoids(cfg, joueurs));
        signalCompletion(cfg, joueurs).ifPresent(lignes::add);

        if (lignes.isEmpty()) return 0;

        String titre = "À surveiller — " + moment + " (" + lignes.size() + ")";
        String corps = String.join("\n", lignes);
        dispatcher.versStaff(equipeId, TypeNotification.DIGEST, titre, corps,
                "/dashboard", null, Priorite.NORMALE);
        return lignes.size();
    }

    // ── Charge (ACWR) + readiness, depuis l'analytics Python ──
    private List<String> signauxAnalytics(NotifConfigEquipe cfg, List<Joueur> joueurs) {
        Map<UUID, String> noms = new HashMap<>();
        joueurs.forEach(j -> noms.put(j.getId(), nom(j)));
        List<String> out = new ArrayList<>();
        try {
            Object res = restTemplate.getForObject(pythonApiUrl + "/api/predictions/equipe", List.class);
            if (!(res instanceof List<?> list)) return out;
            double acwrHaut = cfg.getSeuilAcwrHaut().doubleValue();
            double acwrBas = cfg.getSeuilAcwrBas().doubleValue();
            double readyMin = cfg.getSeuilReadinessMin().doubleValue();
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m)) continue;
                UUID jid = parseUuid(m.get("joueur_id"));
                if (jid == null || !noms.containsKey(jid)) continue;
                String nom = noms.get(jid);
                Double acwr = toDouble(m.get("acwr"));
                if (acwr != null && (acwr >= acwrHaut || acwr <= acwrBas)) {
                    out.add("• " + nom + " — charge ACWR " + arrondi(acwr));
                }
                Double ready = toDouble(m.get("readiness"));
                if (ready != null && ready < readyMin) {
                    out.add("• " + nom + " — readiness basse (" + arrondi(ready) + ")");
                }
            }
        } catch (Exception ignore) {
            // analytics indisponible → on n'ajoute pas ces signaux
        }
        return out;
    }

    // ── Items wellness du jour (direction par item) ──
    private List<String> signauxWellness(NotifConfigEquipe cfg, List<Joueur> joueurs) {
        List<String> out = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (Joueur j : joueurs) {
            WellnessQuotidien w = wellnessRepository.findByJoueurIdAndDate(j.getId(), today).orElse(null);
            if (w == null) continue;
            List<String> soucis = new ArrayList<>();
            if (w.getFatigue() != null && w.getFatigue() >= cfg.getSeuilWellnessFatigue()) soucis.add("fatigue");
            if (w.getDouleur() != null && w.getDouleur() >= cfg.getSeuilWellnessDouleur()) soucis.add("douleurs");
            if (w.getStress() != null && w.getStress() >= cfg.getSeuilWellnessStress()) soucis.add("stress");
            if (w.getSommeil() != null && w.getSommeil() >= cfg.getSeuilWellnessSommeil()) soucis.add("sommeil");
            if (w.getHumeur() != null && w.getHumeur() >= cfg.getSeuilWellnessHumeur()) soucis.add("humeur");
            if (!soucis.isEmpty()) {
                out.add("• " + nom(j) + " — ressenti : " + String.join(", ", soucis));
            }
        }
        return out;
    }

    // ── Écart de poids vs poids de forme cible ──
    private List<String> signauxPoids(NotifConfigEquipe cfg, List<Joueur> joueurs) {
        List<String> out = new ArrayList<>();
        double seuil = cfg.getSeuilPoidsMoyen().doubleValue();
        for (Joueur j : joueurs) {
            BigDecimal actuel = j.getPoidsActuel();
            BigDecimal cible = j.getPoidsFormeCible();
            if (actuel == null || cible == null) continue;
            double ecart = actuel.subtract(cible).doubleValue();
            if (Math.abs(ecart) >= seuil) {
                out.add("• " + nom(j) + " — poids " + (ecart > 0 ? "+" : "") + arrondi(ecart) + " kg vs forme");
            }
        }
        return out;
    }

    // ── Taux de complétion wellness du jour ──
    private Optional<String> signalCompletion(NotifConfigEquipe cfg, List<Joueur> joueurs) {
        LocalDate today = LocalDate.now();
        long saisis = joueurs.stream()
                .filter(j -> wellnessRepository.findByJoueurIdAndDate(j.getId(), today).isPresent())
                .count();
        int pct = (int) Math.round(100.0 * saisis / joueurs.size());
        if (pct < cfg.getSeuilCompletionMin()) {
            return Optional.of("• Complétion wellness : " + pct + "% (" + saisis + "/" + joueurs.size() + ")");
        }
        return Optional.empty();
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
