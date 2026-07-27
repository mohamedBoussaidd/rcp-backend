package com.remipreparateur.tactical.importphoto.controller;

import com.remipreparateur.ia.IaFeature;
import com.remipreparateur.shared.security.CurrentUserProvider;
import com.remipreparateur.tactical.importphoto.service.ParametreIaService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Paramètres IA (super-admin) — PROMPTS uniquement : édition des prompts et des
 * toggles LLM des
 * cartes, avec historique/restauration. Les clés autorisées sont DÉRIVÉES du
 * catalogue
 * {@link IaFeature} (prompt + toggle de chaque feature) → une nouvelle carte
 * est éditable sans
 * toucher ce contrôleur. Les quotas ont migré vers {@code /api/admin/ia/quotas}
 * (source unique).
 */
@RestController
@RequestMapping("/api/admin/parametres-ia")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class ParametreIaAdminController {

    /** Clés éditables (fermé) = prompts + toggles de toutes les features IA. */
    private static final List<String> CLES_AUTORISEES =
            // Arrays.stream(IaFeature.values())
            // .flatMap(f -> Stream.of(f.clePrompt(), f.cleToggle()))
            // .filter(Objects::nonNull)
            // .toList();
            Stream.concat(
                    Arrays.stream(IaFeature.values())
                            .flatMap(f -> Stream.of(f.clePrompt(), f.cleToggle()))
                            .filter(Objects::nonNull),
                    Stream.of( // Stream.of() au lieu de Arrays.asList()
                            ParametreIaService.CLE_FOURNISSEUR_GLOBAL,
                            ParametreIaService.CLE_API_KEY_GLOBAL,
                            ParametreIaService.CLE_MODELE_GLOBAL))
                    .toList();

    private final ParametreIaService parametres;
    private final CurrentUserProvider currentUser;

    public ParametreIaAdminController(ParametreIaService parametres, CurrentUserProvider currentUser) {
        this.parametres = parametres;
        this.currentUser = currentUser;
    }

    public record VersionDto(UUID id, String valeur, LocalDateTime createdAt) {
    }

    public record ParametreDto(String cle, String valeur, String defaut, List<VersionDto> historique) {
    }

    public record ValeurRequest(@NotBlank String valeur) {
    }

    @GetMapping("/{cle}")
    public ParametreDto parametre(@PathVariable String cle) {
        verifierCle(cle);
        return new ParametreDto(cle, parametres.valeurBrute(cle), parametres.defaut(cle),
                parametres.historique(cle).stream()
                        .map(h -> new VersionDto(h.getId(), h.getValeur(), h.getCreatedAt()))
                        .toList());
    }

    @PutMapping("/{cle}")
    public ParametreDto modifier(@PathVariable String cle, @RequestBody ValeurRequest req) {
        verifierCle(cle);
        parametres.mettreAJour(cle, req.valeur(), currentUser.current().getId());
        return parametre(cle);
    }

    @PostMapping("/{cle}/restaurer/{historiqueId}")
    public ParametreDto restaurer(@PathVariable String cle, @PathVariable UUID historiqueId) {
        verifierCle(cle);
        parametres.restaurer(cle, historiqueId, currentUser.current().getId());
        return parametre(cle);
    }

    private void verifierCle(String cle) {
        if (!CLES_AUTORISEES.contains(cle)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Paramètre inconnu");
        }
    }
}
