package com.remipreparateur.performance.briefing;

import com.remipreparateur.ia.service.CarteIaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Assistant IA du préparateur — carte « briefing ». POST (et non GET) car la génération consomme du
 * quota IA (appel LLM potentiel) : déclenchée à la demande par un bouton. Gardée par la permission
 * {@code prepa_ia:briefing} (module add-on {@code assistant_briefing}) dans {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/assistant-ia")
@RequiredArgsConstructor
public class BriefingController {

    private final BriefingService briefingService;

    @PostMapping("/briefing")
    public CarteIaService.TexteCarte briefing() {
        return briefingService.generer();
    }
}
