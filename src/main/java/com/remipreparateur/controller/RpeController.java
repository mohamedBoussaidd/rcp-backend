package com.remipreparateur.controller;

import com.remipreparateur.dto.RpeDtos.RpeResponse;
import com.remipreparateur.service.RpeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * RPE de séance (charge subjective) — consultation staff.
 * Lecture : staff (regle SecurityConfig) ; portee filtree par equipe dans le service.
 */
@RestController
@RequestMapping("/api/rpe")
public class RpeController {

    private final RpeService rpeService;

    public RpeController(RpeService rpeService) {
        this.rpeService = rpeService;
    }

    @GetMapping
    public List<RpeResponse> lister(@RequestParam(required = false) UUID joueurId) {
        return rpeService.listerPourStaff(joueurId);
    }
}
