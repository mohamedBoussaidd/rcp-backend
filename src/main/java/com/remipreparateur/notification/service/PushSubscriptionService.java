package com.remipreparateur.notification.service;

import com.remipreparateur.notification.dto.PushDtos.ClePubliqueDto;
import com.remipreparateur.notification.dto.PushDtos.SubscriptionRequest;
import com.remipreparateur.notification.entity.PushSubscription;
import com.remipreparateur.notification.repository.PushSubscriptionRepository;
import com.remipreparateur.shared.security.CurrentUserProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestion des abonnements Web Push (un par device). L'abonnement est rattaché à
 * l'utilisateur courant ; un même endpoint est ré-affecté (upsert) s'il change de compte.
 */
@Service
public class PushSubscriptionService {

    private final PushSubscriptionRepository repository;
    private final CurrentUserProvider currentUser;

    @Value("${app.push.vapid.public-key:}")
    private String publicKey;

    public PushSubscriptionService(PushSubscriptionRepository repository, CurrentUserProvider currentUser) {
        this.repository = repository;
        this.currentUser = currentUser;
    }

    public ClePubliqueDto clePublique() {
        boolean actif = publicKey != null && !publicKey.isBlank();
        return new ClePubliqueDto(actif ? publicKey : null, actif);
    }

    @Transactional
    public void abonner(SubscriptionRequest req) {
        PushSubscription sub = repository.findByEndpoint(req.endpoint()).orElseGet(PushSubscription::new);
        sub.setUserId(currentUser.current().getId());
        sub.setEndpoint(req.endpoint());
        sub.setP256dh(req.p256dh());
        sub.setAuth(req.auth());
        sub.setUserAgent(req.userAgent());
        repository.save(sub);
    }

    @Transactional
    public void desabonner(String endpoint) {
        repository.deleteByEndpoint(endpoint);
    }
}
