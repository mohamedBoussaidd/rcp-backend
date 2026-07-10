package com.remipreparateur.joueur.controller;

import com.remipreparateur.performance.gps.dto.GpsHistoriqueDto;
import com.remipreparateur.performance.gps.dto.VitesseJoueurDto;
import com.remipreparateur.performance.seance.dto.PresenceDtos.AssiduiteJoueur;
import com.remipreparateur.performance.seance.dto.PresenceDtos.AssiduiteResume;
import com.remipreparateur.performance.seance.service.PresenceService;
import com.remipreparateur.joueur.dto.AnnuaireJoueurDto;
import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.shared.security.ScopeResolver;
import com.remipreparateur.joueur.service.JoueurService;
import com.remipreparateur.saison.service.SaisonService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/joueurs")
@RequiredArgsConstructor
public class JoueurController {

    private final JoueurService joueurService;
    private final PresenceService presenceService;
    private final ScopeResolver scopeResolver;
    private final SaisonService saisonService;

    @GetMapping
    public List<Joueur> getAll() {
        return joueurService.findAll();
    }

    /** Annuaire club : personnes + équipes d'appartenance (effectif EN_COURS) + pool des non-assignés. */
    @GetMapping("/annuaire")
    public List<AnnuaireJoueurDto> annuaire() {
        return joueurService.annuaire();
    }

    /** Assigne une personne à l'effectif d'une équipe (saison EN_COURS). */
    @PostMapping("/{id}/equipes/{equipeId}")
    public ResponseEntity<Void> assigner(@PathVariable UUID id, @PathVariable UUID equipeId) {
        saisonService.inscrireAEquipe(id, equipeId);
        return ResponseEntity.noContent().build();
    }

    /** Retire une personne de l'effectif d'une équipe (saison EN_COURS). */
    @DeleteMapping("/{id}/equipes/{equipeId}")
    public ResponseEntity<Void> desassigner(@PathVariable UUID id, @PathVariable UUID equipeId) {
        saisonService.retirerDeEquipe(id, equipeId);
        return ResponseEntity.noContent().build();
    }

    /** Assiduité (résumé léger) de tout l'effectif du périmètre — colonne triable de l'effectif. */
    @GetMapping("/assiduite-equipe")
    public List<AssiduiteResume> getAssiduiteEquipe() {
        return presenceService.assiduiteEquipe();
    }

    /** Bilan d'assiduité du joueur sur la saison active (entraînements) : taux, compteurs, historique. */
    @GetMapping("/{id}/assiduite")
    public AssiduiteJoueur getAssiduite(@PathVariable UUID id) {
        return presenceService.assiduite(id);   // scope vérifié dans le service
    }

    @GetMapping("/{id}")
    public ResponseEntity<Joueur> getById(@PathVariable UUID id) {
        return joueurService.findById(id)
                .map(j -> { scopeResolver.verifieAccesPersonne(j.getId(), j.getClubId()); return ResponseEntity.ok(j); })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Joueur create(@Valid @RequestBody Joueur joueur,
                         @RequestParam(required = false) UUID equipeId) {
        return joueurService.create(joueur, equipeId);   // equipeId (optionnel) = inscription à l'effectif
    }

    @PutMapping("/{id}")
    public ResponseEntity<Joueur> update(@PathVariable UUID id, @Valid @RequestBody Joueur joueur) {
        return joueurService.findById(id).map(existing -> {
            scopeResolver.verifieAccesPersonne(existing.getId(), existing.getClubId());
            joueur.setId(id);
            joueur.setClubId(existing.getClubId());     // on préserve le club de rattachement
            return ResponseEntity.ok(joueurService.save(joueur));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/tous")
    public List<Joueur> getTous() {
        return joueurService.findAllPlayers();
    }

    /** Fiche vitesse (vmax/vmoy en km/h) des joueurs de l'équipe, pour animer les schémas. */
    @GetMapping("/vitesses")
    public List<VitesseJoueurDto> getVitesses() {
        return joueurService.getVitesses();
    }

    @GetMapping("/{id}/gps")
    public ResponseEntity<List<GpsHistoriqueDto>> getHistoriqueGps(@PathVariable UUID id) {
        return joueurService.findById(id).map(j -> {
            scopeResolver.verifieAccesPersonne(j.getId(), j.getClubId());
            return ResponseEntity.ok(joueurService.getHistoriqueGps(id));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        return joueurService.findById(id).map(j -> {
            scopeResolver.verifieAccesPersonne(j.getId(), j.getClubId());
            joueurService.deleteById(id);
            return ResponseEntity.noContent().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }
}
