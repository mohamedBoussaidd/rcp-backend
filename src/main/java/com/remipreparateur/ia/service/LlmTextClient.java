package com.remipreparateur.ia.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Abstraction d'un client texte LLM (Anthropic, OpenAI…). Découple les features IA du SDK/API
 * d'un fournisseur précis : passer d'un provider à l'autre = de la config, pas du code.
 */
public interface LlmTextClient {

    /** Provider géré (ANTHROPIC | OPENAI). */
    String provider();

    /** Génère une réponse texte à partir d'une consigne système + message utilisateur. */
    String generer(IaResolved cfg, String systeme, String utilisateur, int maxTokens);

    /**
     * Génère une réponse texte à partir d'une consigne + une image JPEG (vision). Par défaut non
     * supporté : seuls les providers gérant la vision l'implémentent (import photo…).
     */
    default String genererAvecImage(IaResolved cfg, String consigne, byte[] imageJpeg, int maxTokens) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Le fournisseur " + provider() + " ne gère pas l'analyse d'image.");
    }
}
