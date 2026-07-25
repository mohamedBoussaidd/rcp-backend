package com.remipreparateur.performance.objectif;

import com.remipreparateur.performance.analytics.service.PredictionService;
import com.remipreparateur.shared.security.CurrentUserProvider;
import com.remipreparateur.shared.security.ScopeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Objectif hebdomadaire de charge d'une équipe.
 * <ul>
 *   <li>GET : panneau « Objectif de la semaine » — délégué à Python (cumul semaine, cible A.5,
 *       objectif, atteinte). Gardé par {@code predictions:read} (cf. SecurityConfig).</li>
 *   <li>PUT : définit/efface l'objectif de l'équipe active. Écriture native, gardée par
 *       {@code predictions:write}. 409 si le contexte n'est pas réduit à une seule équipe.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/predictions")
@RequiredArgsConstructor
public class ObjectifHebdoController {

    private final PredictionService predictionService;
    private final ObjectifHebdoRepository repository;
    private final ScopeResolver scopeResolver;
    private final CurrentUserProvider currentUser;

    @GetMapping("/equipe/objectif-hebdo")
    public Object getObjectifHebdo() {
        return predictionService.getObjectifHebdo();
    }

    @PutMapping("/objectif-hebdo")
    public ObjectifHebdoDtos.Reponse setObjectifHebdo(@RequestBody ObjectifHebdoDtos.MajRequest req) {
        UUID equipeId = scopeResolver.equipeActiveUnique();   // 409 si contexte multi-équipes
        ObjectifHebdo o = repository.findByEquipeId(equipeId).orElseGet(() -> {
            ObjectifHebdo n = new ObjectifHebdo();
            n.setEquipeId(equipeId);
            return n;
        });
        o.setObjectifDistanceM(req.objectifDistanceM());
        o.setUpdatedBy(currentUser.current().getId());
        o.setUpdatedAt(LocalDateTime.now());
        repository.save(o);
        return new ObjectifHebdoDtos.Reponse(equipeId, o.getObjectifDistanceM());
    }
}
