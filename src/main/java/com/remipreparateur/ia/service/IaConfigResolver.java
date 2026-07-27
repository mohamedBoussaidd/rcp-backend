package com.remipreparateur.ia.service;

import com.remipreparateur.ia.entity.ClubIaConfig;
import com.remipreparateur.ia.repository.ClubIaConfigRepository;
import com.remipreparateur.tactical.importphoto.service.ParametreIaService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/**
 * Choisit, pour un club, la config IA à utiliser : sa propre clé (déchiffrée) si configurée et
 * active, sinon la clé globale Anthropic (variable d'environnement) — auquel cas les quotas par
 * feature s'appliquent. Point unique de résolution partagé par toutes les features IA.
 */
@Service
@Slf4j
public class IaConfigResolver {

    public static final String MODELE_ANTHROPIC_DEFAUT = "claude-opus-4-8";

    private final ClubIaConfigRepository repository;
    private final CryptoService crypto;
    //teste 
    private final ParametreIaService parametres;
    //

    @Value("${ANTHROPIC_API_KEY:}")
    private String cleGlobaleAnthropic;
    @Value("${OPENAI_API_KEY:}") private String cleGlobaleOpenAi;  // <-- Nouvelle variable

    public IaConfigResolver(ClubIaConfigRepository repository, CryptoService crypto, ParametreIaService parametres) {
        this.repository = repository;
        this.crypto = crypto;
        this.parametres = parametres;
    }

    /** Résout la config IA effective pour un club (repli clé globale si pas de config propre). */
public IaResolved pour(UUID clubId) {
    if (clubId != null) {
        ClubIaConfig c = repository.findById(clubId).orElse(null);
        if (c != null && c.isActif() && c.getCleApiChiffree() != null && !c.getCleApiChiffree().isBlank()) {
            return new IaResolved(c.getProvider(), crypto.dechiffrer(c.getCleApiChiffree()), c.getModele(), false);
        }
    }

    // ✅ NOUVELLE LOGIQUE : lit FOURNISSEUR + MODÈLE depuis ParametreIa
    String fournisseur = parametres.valeurBrute(ParametreIaService.CLE_FOURNISSEUR_GLOBAL);
    String modele = parametres.valeurBrute(ParametreIaService.CLE_MODELE_GLOBAL);
    String apiKey;

    switch (fournisseur) {
        case "OPENAI":
            apiKey = cleGlobaleOpenAi;
            break;
        case "ANTHROPIC":
        default:
            apiKey = cleGlobaleAnthropic;
            break;
    }

    // Si modele est vide, utilise le défaut selon le fournisseur
    if (modele == null || modele.isBlank()) {
        modele = switch (fournisseur) {
            case "OPENAI" -> "gpt-4o";
            default -> MODELE_ANTHROPIC_DEFAUT;
        };
    }

    return new IaResolved(fournisseur, apiKey, modele, true);
}

    /** La clé globale est-elle configurée sur le serveur ? */
    public boolean cleGlobaleDisponible() {
        return cleGlobaleAnthropic != null && !cleGlobaleAnthropic.isBlank();
    }
    // /** Résout la config IA effective pour un club (repli clé globale si pas de config propre). */
    // public IaResolved pour(UUID clubId) {
    //     if (clubId != null) {
    //         ClubIaConfig c = repository.findById(clubId).orElse(null);
    //         if (c != null && c.isActif() && c.getCleApiChiffree() != null && !c.getCleApiChiffree().isBlank()) {
    //             return new IaResolved(c.getProvider(), crypto.dechiffrer(c.getCleApiChiffree()), c.getModele(), false);
    //         }
    //     }
    //     return new IaResolved("ANTHROPIC", cleGlobaleAnthropic, MODELE_ANTHROPIC_DEFAUT, true);
    // }

    // /** La clé globale est-elle configurée sur le serveur ? */
    // public boolean cleGlobaleDisponible() {
    //     return cleGlobaleAnthropic != null && !cleGlobaleAnthropic.isBlank();
    // }
}
