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
import java.util.List;
import java.util.Map;

/**
 * Client texte OpenAI (API REST chat/completions, via HttpClient JDK — pas de dépendance SDK).
 * Non testé en local (pas de clé OPENAI_API_KEY) mais prêt à l'emploi côté prod.
 */
@Service
public class OpenAiTextClient implements LlmTextClient {

    private static final String URL = "https://api.openai.com/v1/chat/completions";

    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public OpenAiTextClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String provider() {
        return "OPENAI";
    }

    @Override
    public String generer(IaResolved cfg, String systeme, String utilisateur, int maxTokens) {
        if (!cfg.cleDisponible()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Clé API OpenAI absente");
        }
        try {
            Map<String, Object> body = Map.of(
                    "model", cfg.modele(),
                    "max_tokens", maxTokens,
                    "messages", List.of(
                            Map.of("role", "system", "content", systeme == null ? "" : systeme),
                            Map.of("role", "user", "content", utilisateur)));
            HttpRequest req = HttpRequest.newBuilder(URI.create(URL))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + cfg.cleApi())
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Appel IA (OpenAI) HTTP " + resp.statusCode());
            }
            JsonNode root = mapper.readTree(resp.body());
            return root.path("choices").path(0).path("message").path("content").asText("");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Appel IA (OpenAI) échoué : " + e.getMessage());
        }
    }
}
