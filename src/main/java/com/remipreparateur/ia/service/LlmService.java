package com.remipreparateur.ia.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/**
 * Point d'entrée unique des features IA : résout la config du club, applique le quota (si clé
 * globale), délègue au bon client provider, puis journalise la consommation. Les features
 * (import photo, générateur de séance…) passent toutes par ici.
 */
@Service
@Slf4j
public class LlmService {

    private final IaConfigResolver resolver;
    private final IaQuotaService quota;
    private final Map<String, LlmTextClient> clients = new HashMap<>();

    public LlmService(IaConfigResolver resolver, IaQuotaService quota, List<LlmTextClient> clientList) {
        this.resolver = resolver;
        this.quota = quota;
        for (LlmTextClient c : clientList) clients.put(c.provider().toUpperCase(), c);
    }

    /** Génère du texte pour une feature d'un club (résolution config + quota + journal). */
    public String genererTexte(UUID clubId, String feature, String systeme, String utilisateur, int maxTokens) {
        IaResolved cfg = resolver.pour(clubId);
        log.info("Appel IA - Fournisseur: {}, Modèle: {}, Feature: {}", 
            cfg.provider(), cfg.modele(), feature);
        if (!cfg.cleDisponible()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "IA non configurée (aucune clé pour ce club ni clé globale sur le serveur).");
        }
        quota.verifierAvant(clubId, feature, cfg);
        LlmTextClient client = clients.get(cfg.provider() == null ? "" : cfg.provider().toUpperCase());
        if (client == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provider IA non supporté : " + cfg.provider());
        }
        String out = client.generer(cfg, systeme, utilisateur, maxTokens);
        quota.enregistrer(clubId, feature, cfg);
        return out;
    }

    /**
     * Analyse une image pour une feature d'un club (résolution config + quota + journal), même
     * chemin que {@link #genererTexte} : clé propre du club → illimité, sinon clé globale plafonnée.
     */
    public String genererAvecImage(UUID clubId, String feature, String consigne, byte[] imageJpeg, int maxTokens) {
        IaResolved cfg = resolver.pour(clubId);
        log.info("Appel IA - Fournisseur: {}, Modèle: {}, Feature: {}", 
            cfg.provider(), cfg.modele(), feature);
        if (!cfg.cleDisponible()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "IA non configurée (aucune clé pour ce club ni clé globale sur le serveur).");
        }
        quota.verifierAvant(clubId, feature, cfg);
        LlmTextClient client = clients.get(cfg.provider() == null ? "" : cfg.provider().toUpperCase());
        if (client == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provider IA non supporté : " + cfg.provider());
        }
        String out = client.genererAvecImage(cfg, consigne, imageJpeg, maxTokens);
        quota.enregistrer(clubId, feature, cfg);
        return out;
    }
}
