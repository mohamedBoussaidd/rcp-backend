package com.remipreparateur.performance.seance.controller;

import com.remipreparateur.performance.seance.dto.SeanceDtos.ContenuAvanceRequest;
import com.remipreparateur.performance.seance.dto.SeanceDtos.ContenuSeance;
import com.remipreparateur.performance.seance.dto.SeanceDtos.DonneeGpsDto;
import com.remipreparateur.performance.seance.dto.SeanceDtos.ExercicesRequest;
import com.remipreparateur.performance.seance.dto.SeanceDtos.GroupesAutoDto;
import com.remipreparateur.performance.seance.dto.SeanceDtos.PerimatchDto;
import com.remipreparateur.performance.seance.dto.SeanceDtos.ResumeSeance;
import com.remipreparateur.performance.seance.entity.Seance;
import com.remipreparateur.shared.security.ScopeResolver;
import com.remipreparateur.performance.seance.service.SeanceFicheService;
import com.remipreparateur.performance.seance.service.SeanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/seances")
@RequiredArgsConstructor
public class SeanceController {

    private final SeanceService seanceService;
    private final SeanceFicheService seanceFicheService;
    private final ScopeResolver scopeResolver;

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
        return seanceService.create(seance);
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

    @PatchMapping("/{id}/devalider")
    public ResponseEntity<Seance> annulerRealisation(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(seanceService.annulerRealisation(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}/donnees")
    public ResponseEntity<List<DonneeGpsDto>> getDonneesGps(@PathVariable UUID id) {
        return seanceService.findById(id)
                .map(s -> {
                    scopeResolver.verifieAcces(s.getEquipeId());
                    List<DonneeGpsDto> dtos = seanceService.findDonneesGpsBySeance(id).stream()
                            .map(d -> new DonneeGpsDto(
                                    d.getJoueur().getId(),
                                    d.getDureeMinutes(),
                                    d.getDistanceTotaleM(),
                                    d.getDistance15kmhM(),
                                    d.getDistance19kmhM(),
                                    d.getDistanceSprint24kmhM(),
                                    d.getDistanceSprint28kmhM(),
                                    d.getNbSprints24kmh(),
                                    d.getVitesseMaxKmh(),
                                    d.getNbAccelerations(),
                                    d.getNbFreinages(),
                                    d.getRatioDistanceMin()))
                            .toList();
                    return ResponseEntity.ok(dtos);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Exercices de la séance (préparation : référence + overrides) ──
    @GetMapping("/{id}/exercices")
    public ContenuSeance getExercices(@PathVariable UUID id) {
        return seanceService.getContenu(id);
    }

    @PutMapping("/{id}/exercices")
    public ContenuSeance remplacerExercices(@PathVariable UUID id, @RequestBody ExercicesRequest req) {
        return seanceService.remplacerExercices(id, req);
    }

    // ── Mode avancé : contenu complet (blocs + lignes + groupes + référentiels) ──
    @GetMapping("/{id}/contenu")
    public ContenuSeance getContenu(@PathVariable UUID id) {
        return seanceService.getContenu(id);
    }

    @PutMapping("/{id}/contenu")
    public ContenuSeance remplacerContenu(@PathVariable UUID id, @RequestBody ContenuAvanceRequest req) {
        return seanceService.remplacerContenuAvance(id, req);
    }

    // ── Fiche séance (résumé), périodisation et groupes auto ──
    @GetMapping("/{id}/resume")
    public ResumeSeance resume(@PathVariable UUID id) {
        return seanceFicheService.resume(id);
    }

    /** Badge J±X pour une équipe et une date (création de séance : la séance n'existe pas encore). */
    @GetMapping("/perimatch")
    public PerimatchDto perimatch(@RequestParam UUID equipeId,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return seanceFicheService.perimatch(equipeId, date);
    }

    /** Groupes calculés (blessés / réathlétisation / disponibles) pour pré-remplir l'onglet Effectifs. */
    @GetMapping("/groupes-auto")
    public GroupesAutoDto groupesAuto(@RequestParam UUID equipeId) {
        return seanceFicheService.groupesAuto(equipeId);
    }

    /** Comptes staff du club (sélecteur d'affectation des blocs du mode avancé). */
    @GetMapping("/staff-club")
    public List<com.remipreparateur.performance.seance.dto.SeanceDtos.StaffRef> staffClub() {
        return seanceFicheService.staffDuClub();
    }

    /** Partage la fiche au staff de l'équipe (notification in-app + Web Push). */
    @PostMapping("/{id}/partage-staff")
    public Map<String, Integer> partagerAuStaff(@PathVariable UUID id) {
        return Map.of("notifies", seanceFicheService.partagerAuStaff(id));
    }
}
