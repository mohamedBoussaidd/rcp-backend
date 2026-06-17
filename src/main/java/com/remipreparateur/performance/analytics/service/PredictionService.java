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

    public Object getChargeCollective(int semaines) {
        String url = pythonApiUrl + "/api/predictions/charge-collective?semaines=" + semaines;
        return restTemplate.getForObject(url, Object.class);
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

        Object res = restTemplate.getForObject(url.toString(), Object.class);
        Scope scope = scopeResolver.resolve();
        if (scope.all()) return res;
        if (scope.none()) return Map.of("seances", List.of(), "joueurs", List.of());
        if (!(res instanceof Map<?, ?> map)) return res;

        // Post-filtre du classement par joueur sur la portée (équipe) de l'utilisateur.
        Set<String> ids = joueurService.findAll().stream()
                .map(j -> j.getId().toString()).collect(Collectors.toSet());
        Object joueurs = map.get("joueurs");
        List<?> joueursFiltres = (joueurs instanceof List<?> list)
                ? list.stream()
                    .filter(o -> o instanceof Map<?, ?> m && ids.contains(String.valueOf(m.get("joueur_id"))))
                    .toList()
                : List.of();
        Object seances = map.get("seances");
        return Map.of("seances", seances != null ? seances : List.of(), "joueurs", joueursFiltres);
    }
}
