package com.remipreparateur.performance.debrief;

import com.remipreparateur.ia.service.CarteIaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Assistant IA du préparateur — carte « debrief de séance ». POST (consomme du quota IA) : déclenché
 * à la demande. Gardé par {@code prepa_ia:debrief} (module add-on {@code assistant_debrief}).
 */
@RestController
@RequestMapping("/api/assistant-ia")
@RequiredArgsConstructor
public class DebriefController {

    private final DebriefService debriefService;

    @PostMapping("/debrief/{seanceId}")
    public CarteIaService.TexteCarte debrief(@PathVariable UUID seanceId) {
        return debriefService.generer(seanceId);
    }
}
