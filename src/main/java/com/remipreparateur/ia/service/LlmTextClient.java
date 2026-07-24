package com.remipreparateur.ia.service;

/**
 * Abstraction d'un client texte LLM (Anthropic, OpenAI…). Découple les features IA du SDK/API
 * d'un fournisseur précis : passer d'un provider à l'autre = de la config, pas du code.
 */
public interface LlmTextClient {

    /** Provider géré (ANTHROPIC | OPENAI). */
    String provider();

    /** Génère une réponse texte à partir d'une consigne système + message utilisateur. */
    String generer(IaResolved cfg, String systeme, String utilisateur, int maxTokens);
}
