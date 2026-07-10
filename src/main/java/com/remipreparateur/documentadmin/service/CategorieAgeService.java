package com.remipreparateur.documentadmin.service;

import com.remipreparateur.documentadmin.dto.DocumentAdminDtos.CategorieAgeRequest;
import com.remipreparateur.documentadmin.dto.DocumentAdminDtos.CategorieAgeResponse;
import com.remipreparateur.documentadmin.entity.CategorieAge;
import com.remipreparateur.documentadmin.repository.CategorieAgeRepository;
import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.saison.entity.Saison;
import com.remipreparateur.saison.repository.SaisonRepository;
import com.remipreparateur.shared.security.ScopeResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Catégories d'âge configurables par club (ex. U15, U17...). Rattachées à Paramètres
 * (permission {@code configuration:*}) : concept réutilisable au-delà des documents administratifs,
 * pas un sous-réglage du module documents.
 *
 * <p>Âge = ÂGE ATTEINT DANS LA SAISON, pas année de naissance figée : {@code age = anneeDebutSaison
 * - anneeNaissance(joueur)}. Un joueur surclassé (équipe différente de sa catégorie réelle) est géré
 * par construction : le calcul ignore totalement l'équipe, seule la date de naissance compte.
 */
@Service
public class CategorieAgeService {

    private final CategorieAgeRepository repository;
    private final SaisonRepository saisonRepository;
    private final ScopeResolver scopeResolver;

    public CategorieAgeService(CategorieAgeRepository repository, SaisonRepository saisonRepository,
                               ScopeResolver scopeResolver) {
        this.repository = repository;
        this.saisonRepository = saisonRepository;
        this.scopeResolver = scopeResolver;
    }

    @Transactional(readOnly = true)
    public List<CategorieAgeResponse> lister() {
        UUID clubId = scopeResolver.clubActif();
        return repository.findByClubIdOrderByOrdreAsc(clubId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public CategorieAgeResponse creer(CategorieAgeRequest req) {
        UUID clubId = scopeResolver.clubActif();
        String code = normaliserCode(req.code());
        if (repository.findByClubIdAndCodeIgnoreCase(clubId, code).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Une catégorie « " + code + " » existe déjà");
        }
        CategorieAge c = new CategorieAge();
        c.setClubId(clubId);
        c.setCode(code);
        appliquer(c, req);
        return toResponse(repository.save(c));
    }

    @Transactional
    public CategorieAgeResponse modifier(UUID id, CategorieAgeRequest req) {
        CategorieAge c = categorieChecke(id);
        appliquer(c, req);
        return toResponse(repository.save(c));
    }

    private void appliquer(CategorieAge c, CategorieAgeRequest req) {
        if (req.libelle() == null || req.libelle().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le libellé est obligatoire");
        }
        if (req.ageMin() != null && req.ageMax() != null && req.ageMin() > req.ageMax()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L'âge minimum doit être ≤ à l'âge maximum");
        }
        c.setLibelle(req.libelle().trim());
        c.setAgeMin(req.ageMin() != null ? req.ageMin().shortValue() : null);
        c.setAgeMax(req.ageMax() != null ? req.ageMax().shortValue() : null);
        c.setOrdre(req.ordre() != null ? req.ordre().shortValue() : 0);
        c.setActif(req.actif() == null || req.actif());
        validerChevauchement(c);
    }

    /** Deux catégories actives d'un même club ne doivent jamais se chevaucher (mêmes bornes d'âge). */
    private void validerChevauchement(CategorieAge candidate) {
        if (!candidate.isActif()) return;
        List<CategorieAge> autres = repository.findByClubIdOrderByOrdreAsc(candidate.getClubId());
        for (CategorieAge autre : autres) {
            if (!autre.isActif() || autre.getId() != null && autre.getId().equals(candidate.getId())) continue;
            if (seChevauchent(candidate, autre)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Cette tranche d'âge chevauche « " + autre.getLibelle() + " »");
            }
        }
    }

    private boolean seChevauchent(CategorieAge a, CategorieAge b) {
        int aMin = a.getAgeMin() == null ? Integer.MIN_VALUE : a.getAgeMin();
        int aMax = a.getAgeMax() == null ? Integer.MAX_VALUE : a.getAgeMax();
        int bMin = b.getAgeMin() == null ? Integer.MIN_VALUE : b.getAgeMin();
        int bMax = b.getAgeMax() == null ? Integer.MAX_VALUE : b.getAgeMax();
        return aMin <= bMax && bMin <= aMax;
    }

    /** Codes actifs connus du club (pour valider {@code categories_age} d'un type de document). */
    @Transactional(readOnly = true)
    public Set<String> listerCodesConnus(UUID clubId) {
        return repository.findByClubIdAndActifTrueOrderByOrdreAsc(clubId).stream()
                .map(CategorieAge::getCode).collect(Collectors.toSet());
    }

    /**
     * Catégorie du joueur pour le club donné, ou {@code null} si sa date de naissance est
     * inconnue ou hors de toute tranche configurée. Utilisable hors contexte HTTP (scheduler) :
     * ne dépend PAS du {@link ScopeResolver}, {@code clubId} est fourni explicitement.
     */
    @Transactional(readOnly = true)
    public CategorieAge calculerPour(Joueur joueur, UUID clubId) {
        if (joueur.getDateNaissance() == null || clubId == null) return null;
        Saison saison = saisonRepository.findFirstByClubIdAndStatut(clubId, "EN_COURS").orElse(null);
        int anneeRef = saison != null ? saison.getDateDebut().getYear() : LocalDate.now().getYear();
        int age = anneeRef - joueur.getDateNaissance().getYear();
        return repository.findByClubIdAndActifTrueOrderByOrdreAsc(clubId).stream()
                .filter(c -> (c.getAgeMin() == null || age >= c.getAgeMin())
                        && (c.getAgeMax() == null || age <= c.getAgeMax()))
                .findFirst().orElse(null);
    }

    private CategorieAge categorieChecke(UUID id) {
        CategorieAge c = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Catégorie introuvable"));
        scopeResolver.verifieAccesClub(c.getClubId());
        return c;
    }

    private String normaliserCode(String code) {
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le code est obligatoire");
        }
        return code.trim().toUpperCase().replaceAll("[^A-Z0-9_]+", "_");
    }

    private CategorieAgeResponse toResponse(CategorieAge c) {
        return new CategorieAgeResponse(c.getId(), c.getCode(), c.getLibelle(),
                c.getAgeMin() != null ? c.getAgeMin().intValue() : null,
                c.getAgeMax() != null ? c.getAgeMax().intValue() : null,
                c.getOrdre(), c.isActif());
    }
}
