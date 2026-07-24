package com.remipreparateur.ia.service;

import com.remipreparateur.club.entity.Club;
import com.remipreparateur.club.repository.ClubRepository;
import com.remipreparateur.ia.entity.ClubIaConfig;
import com.remipreparateur.ia.repository.ClubIaConfigRepository;
import com.remipreparateur.shared.security.CurrentUserProvider;
import com.remipreparateur.tactical.importphoto.service.ParametreIaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Administration (SUPER_ADMIN) des configs IA par club : liste, upsert (clé chiffrée, jamais
 * renvoyée en clair), révocation, et défauts de quota par feature.
 */
@Service
public class IaConfigAdminService {

    /** Features IA soumises à quota sur la clé globale (pour l'écran des quotas). */
    private static final List<String> FEATURES = List.of("import_photo", "generateur_seance");

    private final ClubIaConfigRepository configRepository;
    private final ClubRepository clubRepository;
    private final CryptoService crypto;
    private final ParametreIaService parametres;
    private final IaQuotaService quotaService;
    private final CurrentUserProvider currentUser;

    public IaConfigAdminService(ClubIaConfigRepository configRepository, ClubRepository clubRepository,
                                CryptoService crypto, ParametreIaService parametres,
                                IaQuotaService quotaService, CurrentUserProvider currentUser) {
        this.configRepository = configRepository;
        this.clubRepository = clubRepository;
        this.crypto = crypto;
        this.parametres = parametres;
        this.quotaService = quotaService;
        this.currentUser = currentUser;
    }

    public record ClubIaDto(UUID clubId, String clubNom, String provider, String modele,
                            boolean actif, boolean aCle, String cleMasquee) {}

    public record ConfigRequest(String provider, String cleApi, String modele, Boolean actif) {}

    /** Tous les clubs avec leur config IA (clé masquée). */
    @Transactional(readOnly = true)
    public List<ClubIaDto> listerClubs() {
        Map<UUID, ClubIaConfig> configs = new LinkedHashMap<>();
        configRepository.findAll().forEach(c -> configs.put(c.getClubId(), c));
        List<ClubIaDto> out = new ArrayList<>();
        for (Club club : clubRepository.findAll()) {
            ClubIaConfig c = configs.get(club.getId());
            if (c == null) {
                out.add(new ClubIaDto(club.getId(), club.getNom(), null, null, false, false, null));
            } else {
                boolean aCle = c.getCleApiChiffree() != null && !c.getCleApiChiffree().isBlank();
                String masquee = aCle ? CryptoService.masquer(crypto.dechiffrer(c.getCleApiChiffree())) : null;
                out.add(new ClubIaDto(club.getId(), club.getNom(), c.getProvider(), c.getModele(), c.isActif(), aCle, masquee));
            }
        }
        return out;
    }

    /** Upsert de la config d'un club. Une clé vide/nulle conserve la clé existante. */
    @Transactional
    public ClubIaDto configurer(UUID clubId, ConfigRequest req) {
        Club club = clubRepository.findById(clubId).orElseThrow();
        ClubIaConfig c = configRepository.findById(clubId).orElseGet(() -> {
            ClubIaConfig n = new ClubIaConfig();
            n.setClubId(clubId);
            return n;
        });
        if (req.provider() != null && !req.provider().isBlank()) c.setProvider(req.provider().toUpperCase());
        if (req.modele() != null && !req.modele().isBlank()) c.setModele(req.modele().trim());
        if (c.getModele() == null || c.getModele().isBlank()) c.setModele(IaConfigResolver.MODELE_ANTHROPIC_DEFAUT);
        if (req.actif() != null) c.setActif(req.actif());
        if (req.cleApi() != null && !req.cleApi().isBlank()) {
            c.setCleApiChiffree(crypto.chiffrer(req.cleApi().trim()));
        }
        c.setUpdatedAt(LocalDateTime.now());
        configRepository.save(c);
        boolean aCle = c.getCleApiChiffree() != null && !c.getCleApiChiffree().isBlank();
        String masquee = aCle ? CryptoService.masquer(crypto.dechiffrer(c.getCleApiChiffree())) : null;
        return new ClubIaDto(clubId, club.getNom(), c.getProvider(), c.getModele(), c.isActif(), aCle, masquee);
    }

    /** Révoque (supprime) la config IA d'un club — il retombe sur la clé globale. */
    @Transactional
    public void revoquer(UUID clubId) {
        configRepository.deleteById(clubId);
    }

    // ── Quotas par feature (défauts globaux, clé globale) ──

    @Transactional(readOnly = true)
    public Map<String, Integer> quotasDefauts() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String f : FEATURES) out.put(f, quotaService.quota(null, f));
        return out;
    }

    @Transactional
    public Map<String, Integer> majQuotasDefauts(Map<String, Integer> valeurs) {
        UUID par = currentUser.current().getId();
        valeurs.forEach((feature, valeur) -> {
            if (FEATURES.contains(feature) && valeur != null && valeur > 0) {
                parametres.mettreAJour("quota_" + feature + "_defaut", String.valueOf(valeur), par);
            }
        });
        return quotasDefauts();
    }
}
