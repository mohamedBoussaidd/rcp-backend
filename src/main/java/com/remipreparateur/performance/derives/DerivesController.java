package com.remipreparateur.performance.derives;

import com.remipreparateur.ia.service.CarteIaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Assistant IA du préparateur — carte « dérives & surveillance ». Deux entrées :
 * <ul>
 *   <li>GET : le bundle STRUCTURÉ des dérives (3 axes) — aucun coût IA, affiché tel quel ;</li>
 *   <li>POST /note : la synthèse textuelle (LLM ou gabarit), déclenchée à la demande.</li>
 * </ul>
 * Gardé par {@code prepa_ia:derives} (module add-on {@code assistant_derives}).
 */
@RestController
@RequestMapping("/api/assistant-ia")
@RequiredArgsConstructor
public class DerivesController {

    private final DerivesService derivesService;

    @GetMapping("/derives")
    public Object derives() {
        return derivesService.indicateurs();
    }

    @PostMapping("/derives/note")
    public CarteIaService.TexteCarte note() {
        return derivesService.genererNote();
    }
}
