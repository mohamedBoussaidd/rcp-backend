package com.remipreparateur.ia.service;

import com.remipreparateur.club.entity.Club;
import com.remipreparateur.club.repository.ClubRepository;
import com.remipreparateur.ia.IaFeature;
import com.remipreparateur.ia.entity.ClubIaConfig;
import com.remipreparateur.ia.repository.ClubIaConfigRepository;
import com.remipreparateur.shared.security.CurrentUserProvider;
import com.remipreparateur.tactical.importphoto.entity.ClubParametre;
import com.remipreparateur.tactical.importphoto.repository.ClubParametreRepository;
import com.remipreparateur.tactical.importphoto.service.ParametreIaService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Administration (SUPER_ADMIN) des configs IA par club : liste, upsert (clé chiffrée, jamais
 * renvoyée en clair), révocation, et défauts de quota par feature.
 */
@Service
public class IaConfigAdminService {

    private final ClubIaConfigRepository configRepository;
    private final ClubRepository clubRepository;
    private final CryptoService crypto;
    private final ParametreIaService parametres;
    private final IaQuotaService quotaService;
    private final ClubParametreRepository clubParametreRepository;
    private final CurrentUserProvider currentUser;
    private final NomAssistantService nomAssistantService;

    public IaConfigAdminService(ClubIaConfigRepository configRepository, ClubRepository clubRepository,
                                CryptoService crypto, ParametreIaService parametres,
                                IaQuotaService quotaService, ClubParametreRepository clubParametreRepository,
                                CurrentUserProvider currentUser, NomAssistantService nomAssistantService) {
        this.configRepository = configRepository;
        this.clubRepository = clubRepository;
        this.crypto = crypto;
        this.parametres = parametres;
        this.quotaService = quotaService;
        this.clubParametreRepository = clubParametreRepository;
        this.currentUser = currentUser;
        this.nomAssistantService = nomAssistantService;
    }

    /** {@code nomAssistant} = nom effectif du club (surcharge si posée, sinon la valeur globale). */
    public record ClubIaDto(UUID clubId, String clubNom, String provider, String modele,
                            boolean actif, boolean aCle, String cleMasquee, String nomAssistant) {}

    /**
     * Nomme l'assistant pour un club (offre « nommez votre assistant ») — {@code nom} vide retire la
     * surcharge et fait retomber le club sur le nom global. Aucune migration : la valeur vit dans
     * {@code club_parametre}, la table clé/valeur des surcharges par club.
     */
    @Transactional
    public List<ClubIaDto> definirNomAssistant(UUID clubId, String nom) {
        clubRepository.findById(clubId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Club introuvable"));
        nomAssistantService.definirPourClub(clubId, nom);
        return listerClubs();
    }

    public record ConfigRequest(String provider, String cleApi, String modele, Boolean actif) {}

    // ── Catalogue des features IA (drive l'écran d'admin : onglets Prompts + Quotas) ──
    public record FeatureDto(String code, String libelle, boolean prompt, boolean toggle,
                             String clePrompt, String cleToggle) {}

    public List<FeatureDto> features() {
        return Arrays.stream(IaFeature.values())
                .map(f -> new FeatureDto(f.code(), f.libelle(), f.aPrompt(), f.aToggle(),
                        f.clePrompt(), f.cleToggle()))
                .toList();
    }

    // ── Quotas unifiés (toutes features) : défaut global + surcharge par club ──
    public record QuotaClubLigne(UUID clubId, String clubNom, Integer surcharge, int effectif, long consomme) {}
    public record QuotaFeatureDto(String feature, String libelle, int defautGlobal, List<QuotaClubLigne> clubs) {}

    /** Tous les clubs avec leur config IA (clé masquée). */
    @Transactional(readOnly = true)
    public List<ClubIaDto> listerClubs() {
        Map<UUID, ClubIaConfig> configs = new LinkedHashMap<>();
        configRepository.findAll().forEach(c -> configs.put(c.getClubId(), c));
        List<ClubIaDto> out = new ArrayList<>();
        for (Club club : clubRepository.findAll()) {
            ClubIaConfig c = configs.get(club.getId());
            String nomAssistant = nomAssistantService.pour(club.getId());
            if (c == null) {
                out.add(new ClubIaDto(club.getId(), club.getNom(), null, null, false, false, null, nomAssistant));
            } else {
                boolean aCle = c.getCleApiChiffree() != null && !c.getCleApiChiffree().isBlank();
                String masquee = aCle ? CryptoService.masquer(crypto.dechiffrer(c.getCleApiChiffree())) : null;
                out.add(new ClubIaDto(club.getId(), club.getNom(), c.getProvider(), c.getModele(),
                        c.isActif(), aCle, masquee, nomAssistant));
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
        return new ClubIaDto(clubId, club.getNom(), c.getProvider(), c.getModele(), c.isActif(), aCle,
                masquee, nomAssistantService.pour(clubId));
    }

    /** Révoque (supprime) la config IA d'un club — il retombe sur la clé globale. */
    @Transactional
    public void revoquer(UUID clubId) {
        configRepository.deleteById(clubId);
    }

    // ── Quotas unifiés : par feature, défaut global (clé plateforme) + surcharge par club ──

    /**
     * Tableau des quotas pour l'écran d'admin : une entrée par feature IA, avec le défaut global et,
     * pour chaque club, sa surcharge éventuelle, son quota effectif et sa consommation du jour.
     */
    @Transactional(readOnly = true)
    public List<QuotaFeatureDto> quotas() {
        List<Club> clubs = clubRepository.findAll();
        List<QuotaFeatureDto> out = new ArrayList<>();
        for (IaFeature f : IaFeature.values()) {
            Map<UUID, Integer> surcharges = clubParametreRepository.findByCle(f.cleQuotaClub()).stream()
                    .collect(Collectors.toMap(ClubParametre::getClubId, this::entier, (a, b) -> a));
            List<QuotaClubLigne> lignes = clubs.stream()
                    .map(c -> new QuotaClubLigne(c.getId(), c.getNom(), surcharges.get(c.getId()),
                            quotaService.quota(c.getId(), f.code()), quotaService.consommeAujourdhui(c.getId(), f.code())))
                    .toList();
            out.add(new QuotaFeatureDto(f.code(), f.libelle(), quotaService.quota(null, f.code()), lignes));
        }
        return out;
    }

    /** Fixe le quota global par défaut d'une feature (clé plateforme). */
    @Transactional
    public List<QuotaFeatureDto> majDefaut(String feature, Integer valeur) {
        IaFeature f = exigerFeature(feature);
        if (valeur == null || valeur < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quota invalide");
        parametres.mettreAJour(f.cleQuotaDefaut(), String.valueOf(valeur), currentUser.current().getId());
        return quotas();
    }

    /** Fixe (ou retire, valeur null) la surcharge de quota d'un club pour une feature. */
    @Transactional
    public List<QuotaFeatureDto> majClub(UUID clubId, String feature, Integer valeur) {
        IaFeature f = exigerFeature(feature);
        if (valeur == null) {
            clubParametreRepository.findByClubIdAndCle(clubId, f.cleQuotaClub())
                    .ifPresent(clubParametreRepository::delete);
        } else {
            ClubParametre p = clubParametreRepository.findByClubIdAndCle(clubId, f.cleQuotaClub())
                    .orElseGet(() -> {
                        ClubParametre n = new ClubParametre();
                        n.setClubId(clubId);
                        n.setCle(f.cleQuotaClub());
                        return n;
                    });
            p.setValeur(String.valueOf(Math.max(0, valeur)));
            clubParametreRepository.save(p);
        }
        return quotas();
    }

    private IaFeature exigerFeature(String feature) {
        IaFeature f = IaFeature.parCode(feature);
        if (f == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Feature IA inconnue : " + feature);
        return f;
    }

    private Integer entier(ClubParametre p) {
        try { return Integer.parseInt(p.getValeur().trim()); } catch (NumberFormatException e) { return null; }
    }
}
