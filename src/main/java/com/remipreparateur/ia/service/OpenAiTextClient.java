package com.remipreparateur.ia.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Client du DIALECTE OpenAI (API REST {@code chat/completions}, via HttpClient JDK — pas de
 * dépendance SDK). Il sert OpenAI mais aussi tout fournisseur compatible déclaré dans le catalogue
 * (Mistral, Groq, DeepSeek, OpenRouter, Ollama…) : seule l'URL de base change, elle vient de
 * {@link IaResolved#baseUrl()}.
 */
@Service
public class OpenAiTextClient implements LlmTextClient {

    /** Racine par défaut, utilisée quand le catalogue n'impose pas d'URL (cas d'OpenAI lui-même). */
    private static final String BASE_DEFAUT = "https://api.openai.com/v1";

    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public OpenAiTextClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String dialecte() {
        return "OPENAI";
    }

    /** Endpoint de complétion du fournisseur résolu (son URL de base, sinon celle d'OpenAI). */
    private static String url(IaResolved cfg) {
        String base = (cfg.baseUrl() == null || cfg.baseUrl().isBlank()) ? BASE_DEFAUT : cfg.baseUrl();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/chat/completions";
    }

    @Override
    public String generer(IaResolved cfg, String systeme, String utilisateur, int maxTokens) {
        if (!cfg.cleDisponible()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Clé API absente pour le fournisseur " + cfg.provider());
        }
        try {
            // `max_completion_tokens` (et non `max_tokens`, déprécié) : requis par les modèles
            // récents (gpt-5, o-series) et accepté par gpt-4o — un seul champ pour tous.
            Map<String, Object> body = Map.of(
                    "model", cfg.modele(),
                    "max_completion_tokens", maxTokens,
                    "messages", List.of(
                            Map.of("role", "system", "content", systeme == null ? "" : systeme),
                            Map.of("role", "user", "content", utilisateur)));
            HttpRequest req = HttpRequest.newBuilder(URI.create(url(cfg)))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + cfg.cleApi())
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Appel IA (" + cfg.provider() + ") HTTP " + resp.statusCode() + " : " + tronquer(resp.body()));
            }
            JsonNode root = mapper.readTree(resp.body());
            return root.path("choices").path(0).path("message").path("content").asText("");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Appel IA (" + cfg.provider() + ") échoué : " + e.getMessage());
        }
    }

    @Override
    public String genererAvecImage(IaResolved cfg, String consigne, byte[] imageJpeg, int maxTokens) {
        if (!cfg.cleDisponible()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Clé API absente pour le fournisseur " + cfg.provider());
        }
        try {
            String dataUri = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(imageJpeg);
            // Message multimodal (texte + image en data-URI) : format « chat/completions » vision OpenAI.
            Map<String, Object> body = Map.of(
                    "model", cfg.modele(),
                    "max_completion_tokens", maxTokens,
                    "messages", List.of(Map.of(
                            "role", "user",
                            "content", List.of(
                                    Map.of("type", "text", "text", consigne),
                                    Map.of("type", "image_url", "image_url", Map.of("url", dataUri))))));
            HttpRequest req = HttpRequest.newBuilder(URI.create(url(cfg)))
                    .timeout(Duration.ofSeconds(150))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + cfg.cleApi())
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Appel IA (OpenAI vision) HTTP " + resp.statusCode() + " : " + tronquer(resp.body()));
            }
            JsonNode root = mapper.readTree(resp.body());
            return root.path("choices").path(0).path("message").path("content").asText("");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Appel IA (OpenAI vision) échoué : " + e.getMessage());
        }
    }

    /** Corps d'erreur OpenAI tronqué (message JSON lisible côté admin, sans noyer les logs). */
    private static String tronquer(String s) {
        if (s == null) return "";
        return s.length() > 400 ? s.substring(0, 400) : s;
    }
}
