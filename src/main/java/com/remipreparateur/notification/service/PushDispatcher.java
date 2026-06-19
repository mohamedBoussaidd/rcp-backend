package com.remipreparateur.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remipreparateur.notification.entity.PushSubscription;
import com.remipreparateur.notification.repository.PushSubscriptionRepository;
import jakarta.annotation.PostConstruct;
import nl.martijndwars.webpush.PushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
            // PushService enregistre lui-même le provider BouncyCastle nécessaire au chiffrement.
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
        if (pushService == null) return;
        byte[] payload = payload(notification);
        for (PushSubscription sub : subscriptionRepository.findByUserId(notification.getDestinataireUserId())) {
            envoyerA(sub, payload);
        }
    }

    private void envoyerA(PushSubscription sub, byte[] payload) {
        try {
            nl.martijndwars.webpush.Notification push = new nl.martijndwars.webpush.Notification(
                    sub.getEndpoint(), sub.getP256dh(), sub.getAuth(), payload);
            var response = pushService.send(push);
            int status = response.getStatusLine().getStatusCode();
            if (status == 404 || status == 410) {
                subscriptionRepository.deleteByEndpoint(sub.getEndpoint()); // abonnement expiré
            } else if (status >= 400) {
                log.debug("Web Push {} → HTTP {}", sub.getEndpoint(), status);
            }
        } catch (Exception e) {
            log.debug("Web Push échec ({}) : {}", sub.getEndpoint(), e.getMessage());
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
}
