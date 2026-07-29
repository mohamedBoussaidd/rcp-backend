package com.remipreparateur.ia.service;

import com.remipreparateur.ia.entity.ClubIaConfig;
import com.remipreparateur.ia.entity.IaFournisseur;
import com.remipreparateur.ia.repository.ClubIaConfigRepository;
import com.remipreparateur.tactical.importphoto.service.ParametreIaService;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Choisit, pour un club, la config IA à utiliser. Point unique de résolution partagé par toutes les
 * features IA, en trois niveaux :
 * <ol>
 *   <li>la clé PROPRE du club (chiffrée, saisie par le super-admin) → pas de quota ;</li>
 *   <li>sinon le fournisseur GLOBAL ({@code ia_fournisseur_global}) et la clé saisie dans le
 *       catalogue {@link IaFournisseurService} ;</li>
 *   <li>sinon la variable d'environnement historique du serveur ({@code ANTHROPIC_API_KEY} /
 *       {@code OPENAI_API_KEY}) — d'où la rétrocompatibilité totale des installations existantes.</li>
 * </ol>
 * Les niveaux 2 et 3 sont « clé globale » : les quotas par feature s'y appliquent.
 *
 * <p>Le catalogue fournit aussi le <em>dialecte</em> et l'<em>URL de base</em> : c'est ce qui permet
 * d'ajouter un fournisseur compatible OpenAI sans toucher au code.
 */
@Service
public class IaConfigResolver {

    public static final String MODELE_ANTHROPIC_DEFAUT = "claude-opus-4-8";

    private final ClubIaConfigRepository repository;
    private final CryptoService crypto;
    private final ParametreIaService parametres;
    private final IaFournisseurService fournisseurs;

    public IaConfigResolver(ClubIaConfigRepository repository, CryptoService crypto,
                            ParametreIaService parametres, IaFournisseurService fournisseurs) {
        this.repository = repository;
        this.crypto = crypto;
        this.parametres = parametres;
        this.fournisseurs = fournisseurs;
    }

    /** Résout la config IA effective pour un club (repli config globale si pas de clé propre). */
    public IaResolved pour(UUID clubId) {
        if (clubId != null) {
            ClubIaConfig c = repository.findById(clubId).orElse(null);
            if (c != null && c.isActif() && c.getCleApiChiffree() != null && !c.getCleApiChiffree().isBlank()) {
                String code = c.getProvider();
                IaFournisseur f = fournisseurs.parCode(code).orElse(null);
                return new IaResolved(code, dialecteDe(code, f), f == null ? null : f.getBaseUrl(),
                        crypto.dechiffrer(c.getCleApiChiffree()), modele(c.getModele(), f), false);
            }
        }

        String code = parametres.valeurBrute(ParametreIaService.CLE_FOURNISSEUR_GLOBAL);
        if (code == null || code.isBlank()) code = "ANTHROPIC";
        IaFournisseur f = fournisseurs.parCode(code).orElse(null);
        return new IaResolved(code, dialecteDe(code, f), f == null ? null : f.getBaseUrl(),
                fournisseurs.cleEffective(code),
                modele(parametres.valeurBrute(ParametreIaService.CLE_MODELE_GLOBAL), f), true);
    }

    /**
     * Le fournisseur est-il exploitable pour ce club (une clé résolue, d'où qu'elle vienne) ? Utilisé
     * par les écrans d'administration pour expliquer une IA muette plutôt que la laisser deviner.
     */
    public boolean cleDisponiblePour(UUID clubId) {
        return pour(clubId).cleDisponible();
    }

    /** Un fournisseur absent du catalogue est son propre dialecte (cas des installations d'avant V89). */
    private static String dialecteDe(String code, IaFournisseur f) {
        if (f != null && f.getDialecte() != null) return f.getDialecte();
        return code == null ? "ANTHROPIC" : code.trim().toUpperCase();
    }

    /** Modèle choisi, sinon le modèle par défaut du fournisseur, sinon le défaut historique. */
    private static String modele(String choisi, IaFournisseur f) {
        if (choisi != null && !choisi.isBlank()) return choisi.trim();
        if (f != null && f.getModeleDefaut() != null && !f.getModeleDefaut().isBlank()) {
            return f.getModeleDefaut().trim();
        }
        return MODELE_ANTHROPIC_DEFAUT;
    }
}
