package com.remipreparateur.ia.service;

import com.remipreparateur.tactical.importphoto.service.ParametreIaService;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Moteur ANCRÉ des cartes IA du préparateur (briefing, debrief, dérives…) : à partir d'indicateurs
 * DÉJÀ calculés (jamais de DB brute au LLM), produit un texte soit par le LLM (mise en mots fluide),
 * soit par un GABARIT de repli (variantes de texte + valeurs insérées). Un seul point de décision,
 * réutilisé par chaque carte.
 *
 * <p>Le LLM n'est tenté que si les trois conditions sont réunies : la carte est activée pour le LLM
 * (toggle super-admin {@code ia_carte_<feature>_active}, défaut ON), une clé est résolue (club ou
 * plateforme) et le quota du jour n'est pas épuisé. Toute erreur d'appel retombe silencieusement sur
 * le gabarit : les cartes optionnelles ne cassent jamais. Le chat (LLM obligatoire) n'utilise PAS ce
 * repli — il appellera {@link LlmService} directement.
 */
@Service
public class CarteIaService {

    /** Budget de sortie d'une carte : un paragraphe court, pas un rapport. */
    private static final int MAX_TOKENS = 600;

    private final LlmService llm;
    private final IaConfigResolver resolver;
    private final IaQuotaService quota;
    private final ParametreIaService parametres;

    public CarteIaService(LlmService llm, IaConfigResolver resolver,
                          IaQuotaService quota, ParametreIaService parametres) {
        this.llm = llm;
        this.resolver = resolver;
        this.quota = quota;
        this.parametres = parametres;
    }

    /** Origine du texte rendu : {@code IA} = rédigé par le LLM, {@code GABARIT} = repli local. */
    public record TexteCarte(String source, String texte) {
        public static TexteCarte ia(String t) { return new TexteCarte("IA", t); }
        public static TexteCarte gabarit(String t) { return new TexteCarte("GABARIT", t); }
    }

    /**
     * Produit le texte d'une carte : LLM si disponible, sinon gabarit. Le message utilisateur envoyé
     * au LLM est le bloc d'indicateurs factuels ; la consigne système est le prompt éditable de la carte.
     *
     * @param clubId      club courant (null = super-admin sans contexte → clé plateforme)
     * @param feature     identifiant de la carte (ex. {@code briefing_prepa}) : quota + toggle + prompt
     * @param clePrompt   clé du prompt éditable ({@link ParametreIaService})
     * @param indicateurs bloc texte des indicateurs déjà calculés (jamais de DB brute)
     * @param gabarit     générateur du texte de repli (évalué uniquement si le LLM n'est pas retenu)
     */
    public TexteCarte produire(UUID clubId, String feature, String clePrompt,
                               String indicateurs, Supplier<String> gabarit) {
        if (llmTentable(clubId, feature)) {
            try {
                String systeme = parametres.valeur(clePrompt);
                String texte = llm.genererTexte(clubId, feature, systeme, indicateurs, MAX_TOKENS);
                if (texte != null && !texte.isBlank()) return TexteCarte.ia(texte.trim());
            } catch (RuntimeException e) {
                // Clé absente / quota / erreur provider → repli gabarit, la carte reste utilisable.
            }
        }
        return TexteCarte.gabarit(gabarit.get());
    }

    /** Le LLM est-il tentable pour cette carte ? toggle ON + clé résolue + quota non épuisé. */
    private boolean llmTentable(UUID clubId, String feature) {
        if (!toggleActif(feature)) return false;
        IaResolved cfg = resolver.pour(clubId);
        if (!cfg.cleDisponible()) return false;
        Integer reste = quota.resteAujourdhui(clubId, feature, cfg);
        return reste == null || reste > 0;   // null = clé propre du club → illimité
    }

    /** Toggle LLM de la carte (super-admin), défaut ON : seule la valeur « false » désactive. */
    private boolean toggleActif(String feature) {
        return !"false".equalsIgnoreCase(parametres.valeur("ia_carte_" + feature + "_active").trim());
    }
}
