package com.remipreparateur.notification.controller;

import com.remipreparateur.notification.dto.PushDtos.ClePubliqueDto;
import com.remipreparateur.notification.dto.PushDtos.DesabonnementRequest;
import com.remipreparateur.notification.dto.PushDtos.SubscriptionRequest;
import com.remipreparateur.notification.service.PushSubscriptionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * Abonnements Web Push de l'utilisateur courant (staff ou joueur). Le front récupère la clé
 * publique VAPID puis enregistre/retire l'abonnement de son navigateur.
 */
@RestController
@RequestMapping("/api/notifications/push")
public class PushController {

    private final PushSubscriptionService service;

    public PushController(PushSubscriptionService service) {
        this.service = service;
    }

    @GetMapping("/cle-publique")
    public ClePubliqueDto clePublique() {
        return service.clePublique();
    }

    @PostMapping("/abonnement")
    public void abonner(@Valid @RequestBody SubscriptionRequest req) {
        service.abonner(req);
    }

    @DeleteMapping("/abonnement")
    public void desabonner(@Valid @RequestBody DesabonnementRequest req) {
        service.desabonner(req.endpoint());
    }
}
