package com.remipreparateur.ia.service;

import com.remipreparateur.ia.entity.IaUsage;
import com.remipreparateur.ia.repository.IaUsageRepository;
import com.remipreparateur.tactical.importphoto.entity.ClubParametre;
import com.remipreparateur.tactical.importphoto.repository.ClubParametreRepository;
import com.remipreparateur.tactical.importphoto.service.ParametreIaService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Quotas quotidiens par feature IA, appliqués UNIQUEMENT quand un club utilise la clé globale
 * (pas de clé propre). Défaut global configurable par le super-admin (ParametreIa
 * {@code quota_<feature>_defaut}) + surcharge par club (club_parametre {@code quota_<feature>}).
 * Journalise chaque appel dans {@code ia_usage} (traçabilité + décompte).
 */
@Service
public class IaQuotaService {

    /** Défauts en dur par feature (si aucune valeur en base). */
    private static final Map<String, Integer> DEFAUTS = Map.of(
            "import_photo", 20,
            "generateur_seance", 10);

    private final IaUsageRepository usageRepository;
    private final ClubParametreRepository clubParametreRepository;
    private final ParametreIaService parametres;

    public IaQuotaService(IaUsageRepository usageRepository,
                          ClubParametreRepository clubParametreRepository,
                          ParametreIaService parametres) {
        this.usageRepository = usageRepository;
        this.clubParametreRepository = clubParametreRepository;
        this.parametres = parametres;
    }

    /** Quota du jour pour un club × feature : surcharge club sinon défaut global (sinon défaut en dur). */
    public int quota(UUID clubId, String feature) {
        if (clubId != null) {
            Integer club = clubParametreRepository.findByClubIdAndCle(clubId, "quota_" + feature)
                    .map(this::entier).orElse(null);
            if (club != null) return club;
        }
        return parametres.valeurEntier("quota_" + feature + "_defaut", DEFAUTS.getOrDefault(feature, 10));
    }

    public long consommeAujourdhui(UUID clubId, String feature) {
        return usageRepository.countByClubIdAndFeatureAndJour(clubId, feature, LocalDate.now());
    }

    /** Nombre d'appels restants aujourd'hui, ou {@code null} si le club utilise sa propre clé (illimité). */
    public Integer resteAujourdhui(UUID clubId, String feature, IaResolved cfg) {
        if (!cfg.cleGlobale()) return null;
        return Math.max(0, quota(clubId, feature) - (int) consommeAujourdhui(clubId, feature));
    }

    /** Vérifie le quota AVANT l'appel (clé globale uniquement) ; 429 si la limite est atteinte. */
    public void verifierAvant(UUID clubId, String feature, IaResolved cfg) {
        if (!cfg.cleGlobale()) return;
        int q = quota(clubId, feature);
        if (consommeAujourdhui(clubId, feature) >= q) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Limite IA atteinte pour aujourd'hui (" + q + "/jour) — réessaie demain ou configure une clé pour ton club.");
        }
    }

    /** Journalise un appel réussi. */
    @Transactional
    public void enregistrer(UUID clubId, String feature, IaResolved cfg) {
        IaUsage u = new IaUsage();
        u.setClubId(clubId);
        u.setFeature(feature);
        u.setProvider(cfg.provider());
        u.setModele(cfg.modele());
        u.setCleGlobale(cfg.cleGlobale());
        u.setJour(LocalDate.now());
        usageRepository.save(u);
    }

    private Integer entier(ClubParametre p) {
        try { return Integer.parseInt(p.getValeur().trim()); } catch (Exception e) { return null; }
    }
}
