package com.remipreparateur.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remipreparateur.notification.entity.PushSubscription;
import com.remipreparateur.notification.repository.PushSubscriptionRepository;
import jakarta.annotation.PostConstruct;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Security;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Envoi Web Push (VAPID) d'une notification in-app aux abonnements du destinataire.
 * Désactivé tant que les clés VAPID ne sont pas configurées ({@code app.push.vapid.*}) :
 * dans ce cas les notifications restent purement in-app. Un abonnement expiré (404/410)
 * est supprimé automatiquement.
 */
@Service
public class PushDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PushDispatcher.class);

    private final PushSubscriptionRepository subscriptionRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.push.vapid.public-key:}")
    private String publicKey;
    @Value("${app.push.vapid.private-key:}")
    private String privateKey;
    @Value("${app.push.vapid.subject:mailto:contact@sportgestions.fr}")
    private String subject;

    private PushService pushService;

    public PushDispatcher(PushSubscriptionRepository subscriptionRepository, ObjectMapper objectMapper) {
        this.subscriptionRepository = subscriptionRepository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        if (publicKey == null || publicKey.isBlank() || privateKey == null || privateKey.isBlank()) {
            log.info("Web Push désactivé (clés VAPID non configurées) — notifications in-app uniquement.");
            return;
        }
        try {
            // bcprov-jdk18on n'auto-enregistre PAS le provider "BC" : web-push fait des
            // KeyFactory.getInstance(..., "BC") → on l'enregistre nous-mêmes au démarrage.
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(new BouncyCastleProvider());
            }
            pushService = new PushService(publicKey, privateKey, subject);
            log.info("Web Push activé (VAPID).");
        } catch (Throwable e) {
            // Throwable (et pas Exception) : un NoClassDefFoundError sur BouncyCastle est une
            // Error ; le push reste optionnel et ne doit jamais empêcher le démarrage de l'app.
            log.warn("Web Push : initialisation impossible — {} ({})", e.getMessage(), e.getClass().getSimpleName());
            pushService = null;
        }
    }

    /** Pousse la notification à tous les abonnements push du destinataire (best-effort). */
    public void envoyer(com.remipreparateur.notification.entity.Notification notification) {
        if (pushService == null) {
            log.info("Web Push ignoré (push indisponible : clés VAPID absentes ou init échouée) — notif {} pour user {}",
                    notification.getType(), notification.getDestinataireUserId());
            return;
        }
        byte[] payload = payload(notification);
        var abonnements = subscriptionRepository.findByUserId(notification.getDestinataireUserId());
        log.info("Web Push : envoi notif {} à user {} → {} abonnement(s)",
                notification.getType(), notification.getDestinataireUserId(), abonnements.size());
        for (PushSubscription sub : abonnements) {
            envoyerA(sub, payload);
        }
    }

    private void envoyerA(PushSubscription sub, byte[] payload) {
        try {
            nl.martijndwars.webpush.Notification push = new nl.martijndwars.webpush.Notification(
                    sub.getEndpoint(), sub.getP256dh(), sub.getAuth(), payload);
            var response = pushService.send(push);
            int status = response.getStatusLine().getStatusCode();
            String service = serviceDe(sub.getEndpoint());
            if (status == 404 || status == 410) {
                log.info("Web Push {} → HTTP {} (abonnement expiré, supprimé)", service, status);
                subscriptionRepository.deleteByEndpoint(sub.getEndpoint()); // abonnement expiré
            } else if (status >= 400) {
                log.warn("Web Push {} → HTTP {} (rejeté par le service de push)", service, status);
            } else {
                log.info("Web Push {} → HTTP {} (accepté)", service, status);
            }
        } catch (Exception e) {
            log.warn("Web Push échec ({}) : {}", serviceDe(sub.getEndpoint()), e.getMessage());
        }
    }

    /** Format attendu par @angular/service-worker (SwPush) : objet « notification » + data. */
    private byte[] payload(com.remipreparateur.notification.entity.Notification n) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url", n.getLien() == null ? "/" : n.getLien());
        data.put("type", n.getType().name());
        Map<String, Object> notif = new LinkedHashMap<>();
        notif.put("title", n.getTitre());
        notif.put("body", n.getCorps() == null ? "" : n.getCorps());
        notif.put("icon", "/icons/icon-192x192.png");
        notif.put("data", data);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("notification", notif);
        try {
            return objectMapper.writeValueAsBytes(root);
        } catch (Exception e) {
            return ("{\"notification\":{\"title\":\"" + safe(n.getTitre()) + "\"}}").getBytes();
        }
    }

    private String safe(String s) {
        return s == null ? "" : s.replace("\"", "'");
    }

    /** Identifie le service de push depuis l'endpoint (pour des logs lisibles). */
    private String serviceDe(String endpoint) {
        if (endpoint == null) return "?";
        if (endpoint.contains("push.apple.com")) return "Apple";
        if (endpoint.contains("fcm.googleapis.com") || endpoint.contains("android.googleapis.com")) return "Google/FCM";
        if (endpoint.contains("mozilla.com") || endpoint.contains("push.services.mozilla")) return "Mozilla";
        return "autre";
    }
}
