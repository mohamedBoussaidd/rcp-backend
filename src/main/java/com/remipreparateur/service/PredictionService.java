package com.remipreparateur.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PredictionService {

    private final RestTemplate restTemplate;

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
        String url = pythonApiUrl + "/api/predictions/equipe";
        return restTemplate.getForObject(url, Object.class);
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
