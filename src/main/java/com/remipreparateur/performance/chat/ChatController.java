package com.remipreparateur.performance.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Assistant IA — chat. Deux entrées :
 * <ul>
 *   <li>GET /etat : disponibilité (clé + quota), nom de l'assistant et actions rapides autorisées.
 *       Aucun coût IA — c'est ce qui permet au widget de s'afficher désactivé avec un message clair
 *       plutôt que d'échouer au premier message ;</li>
 *   <li>POST : envoie le fil et récupère la réponse (consomme le quota).</li>
 * </ul>
 * Gardé par {@code assistant_ia:chat} (module add-on {@code assistant_chat}).
 *
 * <p>L'historique n'est pas persisté : il vit côté navigateur et remonte à chaque appel.
 */
@RestController
@RequestMapping("/api/assistant-ia/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    public record ChatRequete(List<ChatService.MessageChat> messages) {}

    @GetMapping("/etat")
    public ChatService.EtatChat etat() {
        return chatService.etat();
    }

    @PostMapping
    public ChatService.MessageChat repondre(@RequestBody ChatRequete requete) {
        return chatService.repondre(requete.messages());
    }
}
