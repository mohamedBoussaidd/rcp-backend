package com.remipreparateur.performance.analytics.service;

import com.remipreparateur.shared.security.Scope;
import com.remipreparateur.shared.security.ScopeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Passerelle vers l'API Python (analyse GPS / IA). Le front passe TOUJOURS par ce back :
 * on résout ici la portée (équipes) via {@link ScopeResolver} — seul juge des accès — puis on
 * la TRANSMET à Python (en-tête {@code X-Contexte-Equipes}) qui filtre côté SQL. Python ne
 * connaît jamais l'utilisateur : il filtre uniquement ce que le back lui demande.
 */
@Service
@RequiredArgsConstructor
public class PredictionService {

    private final RestTemplate restTemplate;
    private final ScopeResolver scopeResolver;

    @Value("${python.api.url}")
    private String pythonApiUrl;

    public Object getRisqueBlessure(UUID joueurId) {
        String url = pythonApiUrl + "/api/predictions/risque/" + joueurId;
        return restTemplate.getForObject(url, Object.class);
    }

    public Object getFatigue(UUID joueurId) {
        String url = pythonApiUrl + "/api/predictions/fatigue/" + joueurId;
        return restTemplate.getForObject(url, Object.class);
    }

    public Object getResumeEquipe() {
        Scope scope = scopeResolver.resolve();
        if (scope.none()) return List.of();
        return appelPythonScope(pythonApiUrl + "/api/predictions/equipe", scope);
    }

    public Object getChargeCollective(int semaines) {
        Scope scope = scopeResolver.resolve();
        if (scope.none()) return Map.of("labels", List.of(), "data", List.of());
        return appelPythonScope(pythonApiUrl + "/api/predictions/charge-collective?semaines=" + semaines, scope);
    }

    public Object getRapportSeance(UUID seanceId) {
        String url = pythonApiUrl + "/api/predictions/seance/" + seanceId + "/rapport";
        return restTemplate.getForObject(url, Object.class);
    }

    public Object getChargeEquipe(String debut, String fin, String types) {
        StringBuilder url = new StringBuilder(pythonApiUrl + "/api/predictions/equipe/charge");
        StringBuilder qs = new StringBuilder();
        if (debut != null && !debut.isBlank()) qs.append(qs.length() == 0 ? "?" : "&").append("debut=").append(debut);
        if (fin   != null && !fin.isBlank())   qs.append(qs.length() == 0 ? "?" : "&").append("fin=").append(fin);
        if (types != null && !types.isBlank()) qs.append(qs.length() == 0 ? "?" : "&").append("types=").append(types);
        url.append(qs);

        Scope scope = scopeResolver.resolve();
        if (scope.none()) return Map.of("seances", List.of(), "joueurs", List.of());
        return appelPythonScope(url.toString(), scope);
    }

    /**
     * Appelle Python en lui transmettant la portée déjà résolue par le back.
     * scope.all() (super-admin sans contexte) → aucun en-tête → Python renvoie tout.
     * Sinon → {@code X-Contexte-Equipes} = équipes autorisées → Python filtre séances + joueurs.
     */
    private Object appelPythonScope(String url, Scope scope) {
        HttpHeaders headers = new HttpHeaders();
        if (!scope.all()) {
            headers.set("X-Contexte-Equipes",
                    scope.equipeIds().stream().map(UUID::toString).collect(Collectors.joining(",")));
        }
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Object.class).getBody();
    }
}
