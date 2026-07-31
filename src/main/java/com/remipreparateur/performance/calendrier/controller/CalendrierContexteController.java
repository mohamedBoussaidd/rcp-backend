package com.remipreparateur.performance.calendrier.controller;

import com.remipreparateur.performance.calendrier.dto.CalendrierDtos.ContexteCalendrier;
import com.remipreparateur.performance.calendrier.service.CalendrierContexteService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Couche « contexte » du calendrier (staff) : ressenti attendu/rempli par jour, retours sRPE
 * par séance, anniversaires. UN appel par période affichée.
 */
@RestController
@RequestMapping("/api/calendrier")
public class CalendrierContexteController {

    private final CalendrierContexteService service;

    public CalendrierContexteController(CalendrierContexteService service) {
        this.service = service;
    }

    @GetMapping("/contexte")
    public ContexteCalendrier contexte(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return service.pourStaff(debut, fin);
    }
}
