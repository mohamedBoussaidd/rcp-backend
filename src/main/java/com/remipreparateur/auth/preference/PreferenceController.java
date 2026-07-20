package com.remipreparateur.auth.preference;

import com.remipreparateur.shared.security.CurrentUserProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Préférences d'interface de l'utilisateur COURANT (self-scope : le token détermine le compte,
 * aucun paramètre d'identité). Accessible à tout utilisateur authentifié — staff et joueur.
 * Clés en usage : {@code mode_avance_seance} (chantier séances enrichies),
 * {@code style_rendu_schema} (chantier vue réaliste 2.5D).
 */
@RestController
@RequestMapping("/api/preferences")
public class PreferenceController {

    private final PreferenceUtilisateurRepository repository;
    private final CurrentUserProvider currentUser;

    public PreferenceController(PreferenceUtilisateurRepository repository, CurrentUserProvider currentUser) {
        this.repository = repository;
        this.currentUser = currentUser;
    }

    public record ValeurRequest(@NotBlank @Size(max = 2000) String valeur) {}

    /** Toutes les préférences du compte courant, en map clé → valeur. */
    @GetMapping
    public Map<String, String> lister() {
        Map<String, String> prefs = new LinkedHashMap<>();
        repository.findByUtilisateurId(currentUser.current().getId())
                .forEach(p -> prefs.put(p.getCle(), p.getValeur()));
        return prefs;
    }

    /** Upsert d'une préférence du compte courant. */
    @PutMapping("/{cle}")
    @Transactional
    public Map<String, String> enregistrer(@PathVariable @Size(max = 60) String cle,
                                           @Valid @RequestBody ValeurRequest req) {
        UUID userId = currentUser.current().getId();
        PreferenceUtilisateur pref = repository.findByUtilisateurIdAndCle(userId, cle)
                .orElseGet(() -> {
                    PreferenceUtilisateur p = new PreferenceUtilisateur();
                    p.setUtilisateurId(userId);
                    p.setCle(cle);
                    return p;
                });
        pref.setValeur(req.valeur());
        pref.setUpdatedAt(LocalDateTime.now());
        repository.save(pref);
        return Map.of(cle, req.valeur());
    }
}
