package com.remipreparateur.ia;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Catalogue des FEATURES IA (source unique) : import photo, générateur, cartes du préparateur…
 * Chaque feature en dérive, par convention de nommage, sa clé de prompt, sa clé de toggle LLM,
 * sa clé de quota par défaut (global) et sa clé de surcharge par club — de sorte qu'ajouter une
 * carte IA = ajouter une entrée ici, et elle apparaît automatiquement dans l'écran d'admin
 * (onglet Prompts + onglet Quotas) et dans la résolution de quota.
 *
 * <p>{@code prompt} = la feature a un prompt éditable. {@code toggle} = la feature est une carte
 * LLM-optionnelle (repli gabarit) pilotée par un toggle super-admin ; les features purement LLM
 * (import photo, générateur) n'en ont pas.
 */
public enum IaFeature {

    GENERATEUR_SEANCE("generateur_seance", "Générateur de séance (IA)", true, false, 10),
    IMPORT_PHOTO("import_photo", "Import photo (IA)", true, false, 20),
    BRIEFING_PREPA("briefing_prepa", "Briefing du préparateur", true, true, 30),
    DEBRIEF_SEANCE("debrief_seance", "Debrief de séance", true, true, 30),
    DERIVES_PREPA("derives_prepa", "Dérives & surveillance", true, true, 20),
    SIMULATION_PREPA("simulation_prepa", "Simulation « et si… »", true, true, 20),
    CHAT_PREPA("chat_prepa", "Assistant conversationnel", true, false, 50);
    // ↑ Une entrée = son onglet Prompts + sa ligne Quotas. Les cartes ont un module add-on dédié
    //   (module + permission + migration), ajouté dans leur propre phase.
    //   Le chat n'a PAS de toggle : il est LLM-obligatoire (aucun gabarit de repli n'est possible
    //   pour une réponse conversationnelle), donc un interrupteur « désactiver le LLM » n'aurait
    //   aucun comportement de repli à offrir — il se contenterait de casser la carte.

    private final String code;
    private final String libelle;
    private final boolean prompt;
    private final boolean toggle;
    private final int quotaDefaut;

    IaFeature(String code, String libelle, boolean prompt, boolean toggle, int quotaDefaut) {
        this.code = code;
        this.libelle = libelle;
        this.prompt = prompt;
        this.toggle = toggle;
        this.quotaDefaut = quotaDefaut;
    }

    public String code() { return code; }
    public String libelle() { return libelle; }
    public boolean aPrompt() { return prompt; }
    public boolean aToggle() { return toggle; }
    public int quotaDefaut() { return quotaDefaut; }

    /** Clé du prompt éditable ({@code prompt_<code>}), ou null si la feature n'a pas de prompt. */
    public String clePrompt() { return prompt ? "prompt_" + code : null; }

    /** Clé du toggle LLM ({@code ia_carte_<code>_active}), ou null si la feature n'est pas une carte. */
    public String cleToggle() { return toggle ? "ia_carte_" + code + "_active" : null; }

    /** Clé du quota global par défaut (ParametreIa). */
    public String cleQuotaDefaut() { return "quota_" + code + "_defaut"; }

    /** Clé de la surcharge de quota par club (club_parametre). */
    public String cleQuotaClub() { return "quota_" + code; }

    private static final Map<String, IaFeature> PAR_CODE =
            Arrays.stream(values()).collect(Collectors.toMap(IaFeature::code, Function.identity()));

    public static IaFeature parCode(String code) { return PAR_CODE.get(code); }
}
