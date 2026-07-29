package com.remipreparateur.performance.simulation;

import com.remipreparateur.ia.service.CarteIaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Assistant IA du préparateur — carte « simulation ». Deux entrées, comme les autres cartes :
 * <ul>
 *   <li>POST : le résultat STRUCTURÉ de la simulation — aucun coût IA, affiché tel quel ;</li>
 *   <li>POST /note : la synthèse textuelle (LLM ou gabarit), déclenchée à la demande.</li>
 * </ul>
 * Gardé par {@code prepa_ia:simulation} (module add-on {@code assistant_simulation}).
 *
 * <p>POST pour les deux car il y a un corps de requête, mais rien n'est écrit : la simulation est
 * une projection en lecture seule.
 */
@RestController
@RequestMapping("/api/assistant-ia/simulation")
@RequiredArgsConstructor
public class SimulationController {

    private final SimulationService simulationService;

    /**
     * Paramètres du scénario « une séance ». Un scénario futur (semaine, retour de blessure…)
     * aura son propre corps et sa propre route sous le même add-on.
     */
    public record SimulationSeanceRequete(UUID typeSeanceId, Integer dureeMinutes) {
        int dureeOuDefaut() {
            return dureeMinutes == null || dureeMinutes <= 0 ? 90 : dureeMinutes;
        }
    }

    @PostMapping
    public Object simuler(@RequestBody SimulationSeanceRequete requete) {
        return simulationService.simuler(requete.typeSeanceId(), requete.dureeOuDefaut());
    }

    @PostMapping("/note")
    public CarteIaService.TexteCarte note(@RequestBody SimulationSeanceRequete requete) {
        return simulationService.genererNote(requete.typeSeanceId(), requete.dureeOuDefaut());
    }
}
