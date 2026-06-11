package com.remipreparateur.performance.analytics.service;

import com.remipreparateur.shared.security.Scope;
import com.remipreparateur.shared.security.ScopeResolver;
import com.remipreparateur.joueur.service.JoueurService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PredictionService {

    private final RestTemplate restTemplate;
    private final ScopeResolver scopeResolver;
    private final JoueurService joueurService;

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
        Object res = restTemplate.getForObject(pythonApiUrl + "/api/predictions/equipe", Object.class);
        Scope scope = scopeResolver.resolve();
        if (scope.all()) return res;
        if (scope.none()) return List.of();
        if (!(res instanceof List<?> list)) return res;
        // Post-filtre : ne garder que les joueurs de la portee (equipe) de l'utilisateur.
        Set<String> ids = joueurService.findAll().stream()
                .map(j -> j.getId().toString()).collect(Collectors.toSet());
        return list.stream()
                .filter(o -> o instanceof Map<?, ?> m && ids.contains(String.valueOf(m.get("joueur_id"))))
                .toList();
    }

    public Object getChargeCollective() {
        String url = pythonApiUrl + "/api/predictions/charge-collective";
        return restTemplate.getForObject(url, Object.class);
    }

    public Object getRapportSeance(UUID seanceId) {
        String url = pythonApiUrl + "/api/predictions/seance/" + seanceId + "/rapport";
        return restTemplate.getForObject(url, Object.class);
    }
}
