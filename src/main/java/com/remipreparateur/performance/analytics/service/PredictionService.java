package com.remipreparateur.performance.analytics.service;

import com.remipreparateur.shared.security.Scope;
import com.remipreparateur.shared.security.ScopeResolver;
import com.remipreparateur.shared.time.Horloge;
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
    private final Horloge horloge;

    @Value("${python.api.url}")
    private String pythonApiUrl;

    public Object getRisqueBlessure(UUID joueurId) {
        String url = pythonApiUrl + "/api/predictions/risque/" + joueurId;
        return appelPython(url);
    }

    public Object getFatigue(UUID joueurId) {
        String url = pythonApiUrl + "/api/predictions/fatigue/" + joueurId;
        return appelPython(url);
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
        return appelPython(url);
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

    /** Panneau « Objectif de la semaine » : cumul de la semaine courante + cible A.5 + objectif, par joueur. */
    public Object getObjectifHebdo() {
        Scope scope = scopeResolver.resolve();
        if (scope.none()) return Map.of("joueurs", List.of());
        return appelPythonScope(pythonApiUrl + "/api/predictions/equipe/objectif-hebdo", scope);
    }

    /**
     * Bundle d'indicateurs COMPACT du briefing (dérivé de l'objectif hebdo) : atteinte de l'objectif +
     * joueurs en surcharge/sous-charge. Consommé par le back (mise en mots LLM ou gabarit), jamais rendu
     * tel quel au front. Portée forwardée à Python, qui filtre.
     */
    public Object getBriefingIndicateurs() {
        Scope scope = scopeResolver.resolve();
        if (scope.none()) return Map.of("effectif", Map.of("nb_joueurs", 0));
        return appelPythonScope(pythonApiUrl + "/api/predictions/equipe/briefing", scope);
    }

    /**
     * Dérives lentes de l'effectif sur ~4 semaines, en 3 axes séparés (volume, haute intensité,
     * ressenti). Portée forwardée à Python. Consommé par la carte web/PWA et la synthèse textuelle.
     */
    public Object getDerives() {
        Scope scope = scopeResolver.resolve();
        if (scope.none()) return Map.of("axes", List.of(), "effectif", Map.of("nb_joueurs", 0));
        return appelPythonScope(pythonApiUrl + "/api/predictions/equipe/derives", scope);
    }

    /**
     * Dérives d'UNE équipe donnée, hors contexte HTTP (appelé par le scheduler de surveillance) :
     * la portée n'est pas résolue via {@link ScopeResolver} mais imposée explicitement.
     */
    public Object getDerivesEquipe(UUID equipeId) {
        HttpHeaders headers = headersBase();
        headers.set("X-Contexte-Equipes", equipeId.toString());
        return restTemplate.exchange(pythonApiUrl + "/api/predictions/equipe/derives",
                HttpMethod.GET, new HttpEntity<>(headers), Object.class).getBody();
    }

    /** Appel Python simple (sans portée d'équipes) transmettant la date simulée si elle est honorée. */
    private Object appelPython(String url) {
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headersBase()), Object.class).getBody();
    }

    /**
     * Appelle Python en lui transmettant la portée déjà résolue par le back.
     * scope.all() (super-admin sans contexte) → aucune portée → Python renvoie tout.
     * Sinon → {@code X-Contexte-Equipes} = équipes autorisées → Python filtre séances + joueurs.
     */
    private Object appelPythonScope(String url, Scope scope) {
        HttpHeaders headers = headersBase();
        if (!scope.all()) {
            headers.set("X-Contexte-Equipes",
                    scope.equipeIds().stream().map(UUID::toString).collect(Collectors.joining(",")));
        }
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Object.class).getBody();
    }

    /**
     * En-têtes de base de tout appel Python. Transmet {@code X-Date-Simulee} UNIQUEMENT quand l'horloge
     * l'honore (appelant SUPER_ADMIN) → l'analytics (fenêtres de charge/ACWR/risque) se calcule à la
     * date simulée. Sinon aucun en-tête temporel → Python reste à la date réelle.
     */
    private HttpHeaders headersBase() {
        HttpHeaders headers = new HttpHeaders();
        if (horloge.estSimulee()) {
            headers.set("X-Date-Simulee", horloge.today().toString());
        }
        return headers;
    }
}
