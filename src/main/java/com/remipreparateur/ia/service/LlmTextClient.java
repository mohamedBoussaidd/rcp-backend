package com.remipreparateur.ia.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Abstraction d'un client texte LLM. Une implémentation = un DIALECTE d'API, pas une marque : le
 * client « OPENAI » sert aussi tous les fournisseurs compatibles OpenAI (Mistral, Groq, DeepSeek,
 * OpenRouter, Ollama…), qui ne diffèrent que par l'URL de base portée par {@link IaResolved}.
 * Ajouter un tel fournisseur est donc de la donnée ; seul un protocole vraiment différent
 * (Gemini natif, Bedrock…) demanderait une nouvelle implémentation ici.
 */
public interface LlmTextClient {

    /** Dialecte d'API géré (ANTHROPIC | OPENAI). */
    String dialecte();

    /** Génère une réponse texte à partir d'une consigne système + message utilisateur. */
    String generer(IaResolved cfg, String systeme, String utilisateur, int maxTokens);

    /**
     * Génère une réponse texte à partir d'une consigne + une image JPEG (vision). Par défaut non
     * supporté : seuls les providers gérant la vision l'implémentent (import photo…).
     */
    default String genererAvecImage(IaResolved cfg, String consigne, byte[] imageJpeg, int maxTokens) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Le fournisseur " + cfg.provider() + " ne gère pas l'analyse d'image.");
    }
}
