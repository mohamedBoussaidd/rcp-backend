package com.remipreparateur.ia.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

/** Client texte Anthropic (SDK officiel). La consigne système est préfixée au message. */
@Service
public class AnthropicTextClient implements LlmTextClient {

    @Override
    public String provider() {
        return "ANTHROPIC";
    }

    @Override
    public String generer(IaResolved cfg, String systeme, String utilisateur, int maxTokens) {
        if (!cfg.cleDisponible()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Clé API Anthropic absente");
        }
        String contenu = (systeme == null || systeme.isBlank()) ? utilisateur : systeme + "\n\n" + utilisateur;
        try {
            AnthropicClient client = AnthropicOkHttpClient.builder().apiKey(cfg.cleApi()).build();
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(cfg.modele())
                    .maxTokens((long) maxTokens)
                    .addUserMessageOfBlockParams(List.of(
                            ContentBlockParam.ofText(TextBlockParam.builder().text(contenu).build())))
                    .build();
            Message message = client.messages().create(params);
            return message.content().stream()
                    .flatMap(b -> b.text().stream())
                    .map(t -> t.text())
                    .collect(Collectors.joining());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Appel IA (Anthropic) échoué : " + e.getMessage());
        }
    }
}
