package com.remipreparateur.performance.poids.controller;

import com.remipreparateur.performance.poids.dto.HistoriquePoidsDto;
import com.remipreparateur.performance.poids.dto.PoidsFicheJoueurDto;
import com.remipreparateur.performance.poids.entity.HistoriquePoids;
import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.performance.poids.repository.HistoriquePoidsRepository;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.shared.security.ScopeResolver;
import com.remipreparateur.joueur.service.JoueurService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/pesees")
@RequiredArgsConstructor
public class HistoriquePoidsController {

    private final HistoriquePoidsRepository poidsRepo;
    private final JoueurRepository joueurRepo;
    private final JoueurService joueurService;
    private final ScopeResolver scopeResolver;

    /** Historique de pesées d'un joueur (du plus récent au plus ancien) */
    @GetMapping
    public List<HistoriquePoidsDto> getByJoueur(@RequestParam UUID joueurId) {
        joueurRepo.findById(joueurId).ifPresent(j -> scopeResolver.verifieAcces(j.getEquipeId()));
        return poidsRepo.findByJoueurIdOrderByDateDesc(joueurId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    /** Vue équipe : tous les joueurs actifs avec leur dernière pesée et l'écart poids de forme */
    @GetMapping("/equipe")
    public List<PoidsFicheJoueurDto> getEquipe() {
        return joueurService.findAll().stream()  // deja filtre par equipe + actifs
                .map(j -> {
                    Optional<HistoriquePoids> derniere = poidsRepo
                            .findByJoueurIdOrderByDateDesc(j.getId())
                            .stream().findFirst();

                    BigDecimal dernierPoids = derniere
                            .map(HistoriquePoids::getPoids)
                            .orElse(j.getPoidsActuel());

                    LocalDate derniereDate = derniere
                            .map(HistoriquePoids::getDate)
                            .orElse(null);

                    BigDecimal ecart = (dernierPoids != null && j.getPoidsFormeCible() != null)
                            ? dernierPoids.subtract(j.getPoidsFormeCible()).setScale(1, RoundingMode.HALF_UP)
                            : null;

                    return new PoidsFicheJoueurDto(
                            j.getId(), j.getNom(), j.getPrenom(), j.getPostePrincipal(),
                            j.getPoidsFormeCible(), derniereDate, dernierPoids, ecart);
                })
                .sorted((a, b) -> {
                    if (a.ecartKg() == null) return 1;
                    if (b.ecartKg() == null) return -1;
                    return b.ecartKg().compareTo(a.ecartKg());
                })
                .toList();
    }

    /** Crée ou met à jour une pesée (upsert sur joueur + date) */
    @PostMapping
    public ResponseEntity<HistoriquePoidsDto> upsert(@RequestBody PeseeRequest req) {
        Optional<Joueur> joueurOpt = joueurRepo.findById(req.joueurId());
        if (joueurOpt.isEmpty()) return ResponseEntity.notFound().build();
        scopeResolver.verifieAcces(joueurOpt.get().getEquipeId());

        HistoriquePoids pesee = poidsRepo
                .findByJoueurIdOrderByDateDesc(req.joueurId())
                .stream()
                .filter(p -> p.getDate().equals(req.date()))
                .findFirst()
                .orElse(new HistoriquePoids());

        Joueur joueur = joueurOpt.get();
        pesee.setJoueur(joueur);
        pesee.setDate(req.date());
        pesee.setPoids(req.poids());
        pesee.setCommentaire(req.commentaire());
        pesee = poidsRepo.save(pesee);

        // Met à jour poids_actuel avec la pesée la plus récente
        poidsRepo.findByJoueurIdOrderByDateDesc(req.joueurId())
                .stream().findFirst()
                .ifPresent(latest -> {
                    joueur.setPoidsActuel(latest.getPoids());
                    joueurRepo.save(joueur);
                });

        return ResponseEntity.ok(toDto(pesee));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Optional<HistoriquePoids> peseeOpt = poidsRepo.findById(id);
        if (peseeOpt.isEmpty()) return ResponseEntity.notFound().build();
        scopeResolver.verifieAcces(peseeOpt.get().getJoueur().getEquipeId());
        poidsRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private HistoriquePoidsDto toDto(HistoriquePoids p) {
        return new HistoriquePoidsDto(
                p.getId(), p.getJoueur().getId(), p.getDate(), p.getPoids(), p.getCommentaire());
    }

    record PeseeRequest(UUID joueurId, LocalDate date, BigDecimal poids, String commentaire) {}
}
