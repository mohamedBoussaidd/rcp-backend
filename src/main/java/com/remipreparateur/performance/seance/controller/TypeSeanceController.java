package com.remipreparateur.performance.seance.controller;

import com.remipreparateur.performance.seance.dto.TypeSeanceDtos.ApparenceRequest;
import com.remipreparateur.performance.seance.dto.TypeSeanceDtos.CiblesRequest;
import com.remipreparateur.performance.seance.dto.TypeSeanceDtos.TypeSeanceResponse;
import com.remipreparateur.performance.seance.entity.TypeSeance;
import com.remipreparateur.performance.seance.entity.TypeSeanceCible;
import com.remipreparateur.performance.seance.repository.TypeSeanceCibleRepository;
import com.remipreparateur.performance.seance.repository.TypeSeanceRepository;
import com.remipreparateur.performance.seance.service.TypeSeanceCatalogueService;
import com.remipreparateur.shared.security.ScopeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/type-seances")
@RequiredArgsConstructor
public class TypeSeanceController {

    /** Couleur hexadécimale courte ou longue — la base stocke jusqu'à 9 caractères. */
    private static final Pattern HEX = Pattern.compile("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$");

    private static final Set<String> PROFILS = Set.of("TERRAIN", "MUSCULATION", "SANS_CHARGE_EXTERNE");

    private final TypeSeanceRepository typeSeanceRepository;
    private final TypeSeanceCibleRepository cibleRepository;
    private final TypeSeanceCatalogueService catalogueService;
    private final ScopeResolver scopeResolver;

    /** Catalogue des types enrichi des réglages du club actif (cibles + couleur). */
    @GetMapping
    public List<TypeSeanceResponse> getAll() {
        return catalogueService.catalogue();
    }

    /** Paramètre les réglages d'un type pour le club actif (upsert) : cibles + couleur. */
    @PutMapping("/{id}/cibles")
    public TypeSeanceResponse setCibles(@PathVariable UUID id, @RequestBody CiblesRequest req) {
        TypeSeance type = typeSeanceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Type de séance introuvable"));
        UUID club = scopeResolver.clubActif();
        TypeSeanceCible cible = cibleRepository.findByClubIdAndTypeSeanceId(club, id)
                .orElseGet(() -> {
                    TypeSeanceCible c = new TypeSeanceCible();
                    c.setClubId(club);
                    c.setTypeSeanceId(id);
                    return c;
                });
        cible.setObjectifDistanceM(req.objectifDistanceM());
        cible.setObjectifDistanceHauteIntensiteM(req.objectifDistanceHauteIntensiteM());
        cible.setObjectifIntensite(req.objectifIntensite());
        // Couleur PAR CLUB (V94) : vide → on repasse au défaut du catalogue.
        cible.setCouleur(couleurValidee(req.couleur()));
        return catalogueService.toResponse(type, cibleRepository.save(cible));
    }

    /**
     * Nature d'un type (TERRAIN / MUSCULATION / SANS_CHARGE_EXTERNE).
     *
     * <p>⚠ Le catalogue des types est GLOBAL (aucun {@code club_id}) : ce réglage vaut pour
     * TOUS les clubs de la plateforme. Il est donc réservé au SUPER_ADMIN — la permission
     * {@code typeseances:write} ne suffit pas, elle est détenue par quatre rôles dans chaque
     * club. La couleur, elle, est passée par club (cf. {@link #setCibles}).
     */
    @PutMapping("/{id}/apparence")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public TypeSeanceResponse setApparence(@PathVariable UUID id, @RequestBody ApparenceRequest req) {
        TypeSeance type = typeSeanceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Type de séance introuvable"));

        if (req.profil() != null) {
            String profil = req.profil().trim().toUpperCase();
            if (!PROFILS.contains(profil)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Profil inconnu : " + profil);
            }
            type.setProfil(profil);
        }
        TypeSeance saved = typeSeanceRepository.save(type);
        UUID club = scopeResolver.clubActif();
        return catalogueService.toResponse(saved,
                cibleRepository.findByClubIdAndTypeSeanceId(club, id).orElse(null));
    }

    /** Couleur hexadécimale validée, ou null si absente/vide. */
    private String couleurValidee(String brute) {
        if (brute == null) return null;
        String couleur = brute.trim();
        if (couleur.isEmpty()) return null;
        if (!HEX.matcher(couleur).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Couleur invalide : attendu un hexadécimal comme #22c55e");
        }
        return couleur;
    }
}
