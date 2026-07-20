package com.remipreparateur.tactical.importphoto.controller;

import com.remipreparateur.club.repository.ClubRepository;
import com.remipreparateur.shared.security.CurrentUserProvider;
import com.remipreparateur.tactical.importphoto.entity.ClubParametre;
import com.remipreparateur.tactical.importphoto.repository.ClubParametreRepository;
import com.remipreparateur.tactical.importphoto.service.ImportPhotoService;
import com.remipreparateur.tactical.importphoto.service.ParametreIaService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Paramètres IA (super-admin) : édition du prompt vision avec historique/restauration,
 * et quotas d'import photo par club (défaut global + surcharges).
 */
@RestController
@RequestMapping("/api/admin/parametres-ia")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class ParametreIaAdminController {

    /** Clés éditables depuis l'écran admin (liste fermée : pas de clé arbitraire). */
    private static final List<String> CLES_AUTORISEES = List.of(
            ParametreIaService.CLE_PROMPT_IMPORT_PHOTO,
            ParametreIaService.CLE_QUOTA_DEFAUT);

    private final ParametreIaService parametres;
    private final ImportPhotoService importPhoto;
    private final ClubParametreRepository clubParametreRepository;
    private final ClubRepository clubRepository;
    private final CurrentUserProvider currentUser;

    public ParametreIaAdminController(ParametreIaService parametres,
                                      ImportPhotoService importPhoto,
                                      ClubParametreRepository clubParametreRepository,
                                      ClubRepository clubRepository,
                                      CurrentUserProvider currentUser) {
        this.parametres = parametres;
        this.importPhoto = importPhoto;
        this.clubParametreRepository = clubParametreRepository;
        this.clubRepository = clubRepository;
        this.currentUser = currentUser;
    }

    public record VersionDto(UUID id, String valeur, LocalDateTime createdAt) {}
    public record ParametreDto(String cle, String valeur, String defaut, List<VersionDto> historique) {}
    public record ValeurRequest(@NotBlank String valeur) {}
    public record QuotaClubDto(UUID clubId, String clubNom, Integer quotaSurcharge, int quotaEffectif, long consommeAujourdhui) {}
    public record QuotaRequest(Integer valeur) {}   // null = retirer la surcharge

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

    // ── Quotas d'import photo par club ──

    @GetMapping("/import-photo/quotas")
    public List<QuotaClubDto> quotas() {
        Map<UUID, Integer> surcharges = clubParametreRepository.findByCle("quota_import_photo").stream()
                .collect(Collectors.toMap(ClubParametre::getClubId, p -> {
                    try { return Integer.parseInt(p.getValeur().trim()); }
                    catch (NumberFormatException e) { return -1; }
                }));
        return clubRepository.findAll().stream()
                .map(c -> new QuotaClubDto(c.getId(), c.getNom(),
                        surcharges.get(c.getId()),
                        importPhoto.quotaDuClub(c.getId()),
                        importPhoto.consommeAujourdhui(c.getId())))
                .toList();
    }

    /** Fixe (ou retire, valeur null) la surcharge de quota d'un club. */
    @PutMapping("/import-photo/quotas/{clubId}")
    @Transactional
    public List<QuotaClubDto> fixerQuota(@PathVariable UUID clubId, @RequestBody QuotaRequest req) {
        if (req.valeur() == null) {
            clubParametreRepository.findByClubIdAndCle(clubId, "quota_import_photo")
                    .ifPresent(clubParametreRepository::delete);
        } else {
            ClubParametre p = clubParametreRepository.findByClubIdAndCle(clubId, "quota_import_photo")
                    .orElseGet(() -> {
                        ClubParametre n = new ClubParametre();
                        n.setClubId(clubId);
                        n.setCle("quota_import_photo");
                        return n;
                    });
            p.setValeur(String.valueOf(Math.max(0, req.valeur())));
            clubParametreRepository.save(p);
        }
        return quotas();
    }

    private void verifierCle(String cle) {
        if (!CLES_AUTORISEES.contains(cle)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Paramètre inconnu");
        }
    }
}
