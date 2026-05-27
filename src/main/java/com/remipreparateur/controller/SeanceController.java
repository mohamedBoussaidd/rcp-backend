package com.remipreparateur.controller;

import com.remipreparateur.entity.DonneeGps;
import com.remipreparateur.entity.Seance;
import com.remipreparateur.service.SeanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/seances")
@RequiredArgsConstructor
public class SeanceController {

    private final SeanceService seanceService;

    @GetMapping
    public List<Seance> getAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        if (debut != null && fin != null) {
            return seanceService.findByPeriode(debut, fin);
        }
        return seanceService.findAll();
    }

    @PostMapping
    public Seance create(@RequestBody Seance seance) {
        if (seance.getStatut() == null) seance.setStatut("PLANIFIEE");
        return seanceService.save(seance);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Seance> update(@PathVariable UUID id, @RequestBody Seance patch) {
        try {
            return ResponseEntity.ok(seanceService.update(id, patch));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        seanceService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/realiser")
    public ResponseEntity<Seance> marquerRealisee(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(seanceService.marquerRealisee(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}/donnees")
    public ResponseEntity<List<DonneeGps>> getDonneesGps(@PathVariable UUID id) {
        return seanceService.findById(id)
                .map(s -> ResponseEntity.ok(seanceService.findDonneesGpsBySeance(id)))
                .orElse(ResponseEntity.notFound().build());
    }
}
