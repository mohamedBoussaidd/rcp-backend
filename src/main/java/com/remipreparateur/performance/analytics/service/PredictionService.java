package com.remipreparateur.performance.analytics.service;

import com.remipreparateur.auth.rbac.FeatureModule;
import com.remipreparateur.auth.rbac.PermissionResolver;
import com.remipreparateur.club.pack.ClubModulesService;
import com.remipreparateur.shared.security.CurrentUserProvider;
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
    private final ClubModulesService clubModulesService;
    private final PermissionResolver permissionResolver;
    private final CurrentUserProvider currentUser;

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

    /** Charge cible du joueur. Exposée par Python de longue date, mais jamais forwardée : la
     *  fiche joueur appelait donc une route inexistante et prenait un 404 à chaque ouverture. */
    public Object getChargeCible(UUID joueurId) {
        String url = pythonApiUrl + "/api/predictions/charge-cible/" + joueurId;
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
        return appelPythonScope(pythonApiUrl + "/api/predictions/equipe/objectif-hebdo", scope, moduleObjectifs());
    }

    /**
     * Trajectoire d'un joueur sur une période : Habituel / Attendu / Retenu semaine par semaine,
     * plus le réalisé. Portée transmise pour que Python refuse un joueur hors périmètre même si le
     * contrôle amont sautait, et en-tête d'add-on parce que la trajectoire n'existe pas sans lui.
     */
    public Object getObjectifTrajectoireJoueur(UUID joueurId, UUID periodeId) {
        Scope scope = scopeResolver.resolve();
        if (scope.none()) return Map.of("semaines", List.of());
        String url = pythonApiUrl + "/api/predictions/joueur/" + joueurId + "/objectif-trajectoire"
                + (periodeId == null ? "" : "?periode_id=" + periodeId);
        return appelPythonScope(url, scope, moduleObjectifs());
    }

    /**
     * Bilan d'une période : prescrit contre réalisé, par métrique et par semaine. Recalculé à
     * chaque appel — aucun bilan figé en base, donc un import GPS arrivé en retard corrige le
     * bilan au lieu de le laisser faux pour toujours.
     */
    public Object getBilanPeriode(UUID periodeId) {
        Scope scope = scopeResolver.resolve();
        if (scope.none()) return Map.of("metriques", List.of(), "semaines", List.of());
        return appelPythonScope(pythonApiUrl + "/api/predictions/equipe/bilan-periode?periode_id=" + periodeId,
                scope, moduleObjectifs());
    }

    /**
     * En-tête d'add-on « Objectifs de performance » : Python ne connaît ni l'utilisateur ni
     * l'abonnement, c'est donc ici qu'on décide si le référentiel (niveau attendu du poste), les
     * objectifs prescrits de la période et le plafonnement ACWR entrent dans le calcul.
     * L'ADOPTION d'un référentiel survit à la désactivation du module : sans ce signal, Python
     * retomberait sur l'add-on alors que le club ne l'a plus. Absent = inactif, jamais l'inverse.
     */
    private Map<String, String> moduleObjectifs() {
        boolean actif;
        try {
            UUID clubId = permissionResolver.clubActif(currentUser.current());
            // Club nul = super-admin hors contexte : il voit tout, comme dans exigeModule().
            actif = clubId == null
                    || clubModulesService.modulesActifs(clubId).contains(FeatureModule.OBJECTIFS_PERFORMANCE.getCode());
        } catch (RuntimeException e) {
            actif = false;   // hors contexte HTTP (scheduler) : on n'active pas un add-on par défaut
        }
        return actif ? Map.of("X-Module-Objectifs", "1") : Map.of();
    }

    /**
     * Bundle d'indicateurs COMPACT du briefing (dérivé de l'objectif hebdo) : atteinte de l'objectif +
     * joueurs en surcharge/sous-charge. Consommé par le back (mise en mots LLM ou gabarit), jamais rendu
     * tel quel au front. Portée forwardée à Python, qui filtre.
     */
    public Object getBriefingIndicateurs() {
        Scope scope = scopeResolver.resolve();
        if (scope.none()) return Map.of("effectif", Map.of("nb_joueurs", 0));
        return appelPythonScope(pythonApiUrl + "/api/predictions/equipe/briefing", scope, moduleObjectifs());
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
     * Simulation « et si… » d'une séance hypothétique : distance attendue par joueur (baseline du
     * même type de séance) et recalcul de l'ACWR. POST parce qu'on envoie un corps, mais l'appel est
     * en LECTURE SEULE côté Python — aucune séance n'est créée. Portée forwardée comme ailleurs.
     */
    public Object postSimulationSeance(UUID typeSeanceId, int dureeMinutes) {
        Scope scope = scopeResolver.resolve();
        if (scope.none()) return Map.of("joueurs", List.of(), "synthese", Map.of("nb_evalues", 0));
        Map<String, Object> corps = new java.util.HashMap<>();
        corps.put("type_seance_id", typeSeanceId == null ? null : typeSeanceId.toString());
        corps.put("duree_minutes", dureeMinutes);
        return postPythonScope(pythonApiUrl + "/api/predictions/equipe/simulation", corps, scope);
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
        return appelPythonScope(url, scope, Map.of());
    }

    /** Idem, avec des en-têtes supplémentaires résolus par le back (ex. add-on actif ou non). */
    private Object appelPythonScope(String url, Scope scope, Map<String, String> extra) {
        HttpHeaders headers = headersBase();
        if (!scope.all()) {
            headers.set("X-Contexte-Equipes",
                    scope.equipeIds().stream().map(UUID::toString).collect(Collectors.joining(",")));
        }
        extra.forEach(headers::set);
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Object.class).getBody();
    }

    /** Idem {@link #appelPythonScope} mais en POST avec un corps JSON (endpoints paramétrés). */
    private Object postPythonScope(String url, Object corps, Scope scope) {
        HttpHeaders headers = headersBase();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        if (!scope.all()) {
            headers.set("X-Contexte-Equipes",
                    scope.equipeIds().stream().map(UUID::toString).collect(Collectors.joining(",")));
        }
        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(corps, headers), Object.class).getBody();
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
