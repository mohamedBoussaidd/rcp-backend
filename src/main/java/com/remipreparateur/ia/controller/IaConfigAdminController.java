package com.remipreparateur.ia.controller;

import com.remipreparateur.ia.service.IaConfigAdminService;
import com.remipreparateur.ia.service.IaConfigAdminService.ClubIaDto;
import com.remipreparateur.ia.service.IaConfigAdminService.ConfigRequest;
import com.remipreparateur.ia.service.IaConfigAdminService.FeatureDto;
import com.remipreparateur.ia.service.IaConfigAdminService.QuotaFeatureDto;
import com.remipreparateur.ia.service.IaFournisseurService;
import com.remipreparateur.ia.service.IaFournisseurService.FournisseurDto;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Console IA super-admin : config (provider + clé + modèle) par club et quotas par feature.
 * Les clés API ne sont jamais renvoyées en clair (masquées).
 */
@RestController
@RequestMapping("/api/admin/ia")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class IaConfigAdminController {

    private final IaConfigAdminService service;
    private final IaFournisseurService fournisseurService;

    public IaConfigAdminController(IaConfigAdminService service, IaFournisseurService fournisseurService) {
        this.service = service;
        this.fournisseurService = fournisseurService;
    }

    @GetMapping("/clubs")
    public List<ClubIaDto> clubs() {
        return service.listerClubs();
    }

    @PutMapping("/clubs/{clubId}")
    public ClubIaDto configurer(@PathVariable UUID clubId, @RequestBody ConfigRequest req) {
        return service.configurer(clubId, req);
    }

    @DeleteMapping("/clubs/{clubId}")
    public void revoquer(@PathVariable UUID clubId) {
        service.revoquer(clubId);
    }

    public record NomAssistantRequest(String nom) {}   // vide/null = retirer la surcharge du club

    /**
     * Nomme l'assistant pour un club (« nommez votre assistant »). Le club transmet le nom souhaité,
     * le super-admin le pose ici — ce qui évite des changements trop fréquents côté club.
     */
    @PutMapping("/clubs/{clubId}/nom-assistant")
    public List<ClubIaDto> nommerAssistant(@PathVariable UUID clubId, @RequestBody NomAssistantRequest req) {
        return service.definirNomAssistant(clubId, req.nom());
    }

    /** Catalogue des features IA (drive les onglets Prompts & Quotas de l'écran d'admin). */
    @GetMapping("/features")
    public List<FeatureDto> features() {
        return service.features();
    }

    // ── Catalogue des fournisseurs IA : ajouter un fournisseur sans redéployer ──

    /** Corps d'upsert. {@code cleApi} vide = clé inchangée (voir {@code IaFournisseurService}). */
    public record FournisseurRequest(String libelle, String dialecte, String baseUrl,
                                     String modeleDefaut, Boolean actif, String cleApi) {}

    @GetMapping("/fournisseurs")
    public List<FournisseurDto> fournisseurs() {
        return fournisseurService.catalogue();
    }

    @PutMapping("/fournisseurs/{code}")
    public List<FournisseurDto> majFournisseur(@PathVariable String code, @RequestBody FournisseurRequest req) {
        fournisseurService.enregistrer(code, req.libelle(), req.dialecte(), req.baseUrl(),
                req.modeleDefaut(), req.actif(), req.cleApi());
        return fournisseurService.catalogue();
    }

    /** Efface la clé saisie : le fournisseur retombe sur sa variable d'environnement, s'il en a une. */
    @DeleteMapping("/fournisseurs/{code}/cle")
    public List<FournisseurDto> revoquerCle(@PathVariable String code) {
        fournisseurService.revoquerCle(code);
        return fournisseurService.catalogue();
    }

    @DeleteMapping("/fournisseurs/{code}")
    public List<FournisseurDto> supprimerFournisseur(@PathVariable String code) {
        fournisseurService.supprimer(code);
        return fournisseurService.catalogue();
    }

    // ── Quotas unifiés : défaut global + surcharge par club, pour toutes les features ──

    @GetMapping("/quotas")
    public List<QuotaFeatureDto> quotas() {
        return service.quotas();
    }

    public record ValeurRequest(Integer valeur) {}   // null = retirer la surcharge (côté club)

    @PutMapping("/quotas/defaut/{feature}")
    public List<QuotaFeatureDto> majDefaut(@PathVariable String feature, @RequestBody ValeurRequest req) {
        return service.majDefaut(feature, req.valeur());
    }

    @PutMapping("/quotas/club/{clubId}/{feature}")
    public List<QuotaFeatureDto> majClub(@PathVariable UUID clubId, @PathVariable String feature,
                                         @RequestBody ValeurRequest req) {
        return service.majClub(clubId, feature, req.valeur());
    }
}
