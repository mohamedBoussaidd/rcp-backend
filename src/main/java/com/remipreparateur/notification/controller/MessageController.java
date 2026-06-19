package com.remipreparateur.notification.controller;

import com.remipreparateur.notification.dto.MessageDtos.CapaciteEnvoiDto;
import com.remipreparateur.notification.dto.MessageDtos.MessageEnvoyeDto;
import com.remipreparateur.notification.dto.MessageDtos.MessageRequest;
import com.remipreparateur.notification.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Chat 1-sens. Staff et joueurs autorisés envoient des messages ; chaque destinataire le
 * reçoit dans ses notifications. {@code /capacite} pilote l'affichage du widget.
 */
@RestController
@RequestMapping("/api/notifications/messages")
public class MessageController {

    private final MessageService service;

    public MessageController(MessageService service) {
        this.service = service;
    }

    @GetMapping("/capacite")
    public CapaciteEnvoiDto capacite() {
        return service.capacite();
    }

    @PostMapping
    public Map<String, Integer> envoyer(@Valid @RequestBody MessageRequest req) {
        return Map.of("envoyes", service.envoyer(req));
    }

    @GetMapping("/envoyes")
    public List<MessageEnvoyeDto> envoyes() {
        return service.envoyes();
    }

    @GetMapping("/destinataires")
    public List<com.remipreparateur.notification.dto.MessageDtos.DestinataireDto> destinataires() {
        return service.destinataires();
    }
}
