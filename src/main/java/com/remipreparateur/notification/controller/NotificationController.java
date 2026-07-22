package com.remipreparateur.notification.controller;

import com.remipreparateur.notification.dto.NotificationDtos.CompteurResponse;
import com.remipreparateur.notification.dto.NotificationDtos.NotificationPage;
import com.remipreparateur.notification.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Notifications in-app du destinataire courant (staff ET joueur). Lecture scopée par token
 * (chaque utilisateur ne voit que ses propres notifications). La cloche poll {@code /compteur}.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    /** Liste paginée des notifications du destinataire courant (+ compteur de non-lus). */
    @GetMapping
    public NotificationPage lister(@RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size,
                                   @RequestParam(required = false) String categorie) {
        return service.lister(page, Math.min(size, 50), categorie);
    }

    /** Compteur de non-lus — endpoint léger pour le polling de la cloche. */
    @GetMapping("/compteur")
    public CompteurResponse compteur() {
        return service.compteurNonLus();
    }

    @PostMapping("/{id}/lu")
    public void marquerLu(@PathVariable UUID id) {
        service.marquerLu(id);
    }

    @PostMapping("/lire-tout")
    public void marquerToutLu() {
        service.marquerToutLu();
    }

    /** Supprime toutes les notifications déjà lues du destinataire courant. */
    @DeleteMapping("/lues")
    public void viderLues() {
        service.viderLues();
    }

    /** Supprime une notification du destinataire courant. */
    @DeleteMapping("/{id}")
    public void supprimer(@PathVariable UUID id) {
        service.supprimer(id);
    }
}
