package com.remipreparateur.ia.service;

import com.remipreparateur.ia.entity.ClubIaConfig;
import com.remipreparateur.ia.repository.ClubIaConfigRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Choisit, pour un club, la config IA à utiliser : sa propre clé (déchiffrée) si configurée et
 * active, sinon la clé globale Anthropic (variable d'environnement) — auquel cas les quotas par
 * feature s'appliquent. Point unique de résolution partagé par toutes les features IA.
 */
@Service
public class IaConfigResolver {

    public static final String MODELE_ANTHROPIC_DEFAUT = "claude-opus-4-8";

    private final ClubIaConfigRepository repository;
    private final CryptoService crypto;

    @Value("${ANTHROPIC_API_KEY:}")
    private String cleGlobaleAnthropic;

    public IaConfigResolver(ClubIaConfigRepository repository, CryptoService crypto) {
        this.repository = repository;
        this.crypto = crypto;
    }

    /** Résout la config IA effective pour un club (repli clé globale si pas de config propre). */
    public IaResolved pour(UUID clubId) {
        if (clubId != null) {
            ClubIaConfig c = repository.findById(clubId).orElse(null);
            if (c != null && c.isActif() && c.getCleApiChiffree() != null && !c.getCleApiChiffree().isBlank()) {
                return new IaResolved(c.getProvider(), crypto.dechiffrer(c.getCleApiChiffree()), c.getModele(), false);
            }
        }
        return new IaResolved("ANTHROPIC", cleGlobaleAnthropic, MODELE_ANTHROPIC_DEFAUT, true);
    }

    /** La clé globale est-elle configurée sur le serveur ? */
    public boolean cleGlobaleDisponible() {
        return cleGlobaleAnthropic != null && !cleGlobaleAnthropic.isBlank();
    }
}
