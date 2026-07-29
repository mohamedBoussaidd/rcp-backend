package com.remipreparateur.performance.chat;

import com.remipreparateur.auth.entity.Role;
import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.auth.rbac.Permission;
import com.remipreparateur.ia.IaFeature;
import com.remipreparateur.ia.service.IaConfigResolver;
import com.remipreparateur.ia.service.IaQuotaService;
import com.remipreparateur.ia.service.IaResolved;
import com.remipreparateur.ia.service.LlmService;
import com.remipreparateur.ia.service.NomAssistantService;
import com.remipreparateur.shared.security.ContexteActif;
import com.remipreparateur.shared.security.ContexteActifHolder;
import com.remipreparateur.shared.security.CurrentUserProvider;
import com.remipreparateur.tactical.importphoto.service.ParametreIaService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * Chat de l'assistant (carte 3). Contrairement aux autres cartes, il est <strong>LLM-obligatoire</strong> :
 * une réponse conversationnelle n'a pas de gabarit de repli crédible. Il appelle donc {@link LlmService}
 * directement, sans passer par {@code CarteIaService}, et se désactive proprement (message explicite)
 * quand aucune clé n'est configurée ou que le quota du jour est épuisé.
 *
 * <p><strong>Ancrage.</strong> Le contexte envoyé au modèle est assemblé à partir des seuls
 * {@link ContexteChat} dont l'utilisateur détient la permission — voir cette interface pour la
 * garantie de confidentialité. Aucune donnée brute, aucune requête libre en base.
 *
 * <p><strong>Actions rapides.</strong> Plutôt que de laisser le modèle choisir des outils (coûteux et
 * imprévisible), le service publie la liste des cartes que cet utilisateur peut déclencher. Le front
 * en fait des boutons qui appellent les endpoints existants : même valeur, coût constant.
 */
@Service
public class ChatService {

    public static final String FEATURE = "chat_prepa";

    /** Budget de sortie d'une réponse de chat : quelques phrases, pas un rapport. */
    private static final int MAX_TOKENS = 700;

    /** Nombre maximum de messages d'historique repris dans le prompt (garde le coût borné). */
    private static final int MAX_HISTORIQUE = 12;

    private final LlmService llm;
    private final IaConfigResolver resolver;
    private final IaQuotaService quota;
    private final ParametreIaService parametres;
    private final NomAssistantService nomAssistant;
    private final CurrentUserProvider currentUser;
    private final List<ContexteChat> contextes;

    public ChatService(LlmService llm, IaConfigResolver resolver, IaQuotaService quota,
                       ParametreIaService parametres, NomAssistantService nomAssistant,
                       CurrentUserProvider currentUser, List<ContexteChat> contextes) {
        this.llm = llm;
        this.resolver = resolver;
        this.quota = quota;
        this.parametres = parametres;
        this.nomAssistant = nomAssistant;
        this.currentUser = currentUser;
        this.contextes = contextes;
    }

    /** Un message du fil. {@code role} = {@code user} | {@code assistant}. */
    public record MessageChat(String role, String contenu) {}

    /** Raccourci proposé dans le widget : déclenche une carte IA existante. */
    public record ActionRapide(String code, String libelle, String endpoint, String methode) {}

    /**
     * État du chat pour l'utilisateur courant : le front s'en sert pour afficher le widget, le nom de
     * l'assistant, les boutons d'action, ou un message d'indisponibilité.
     *
     * @param raison {@code null} si disponible, sinon {@code PAS_DE_CLE} ou {@code QUOTA_EPUISE}
     */
    public record EtatChat(boolean disponible, String raison, String message, String nom,
                           List<ActionRapide> actions) {}

    public EtatChat etat() {
        UUID clubId = clubCourant();
        String nom = nomAssistant.pour(clubId);
        List<ActionRapide> actions = actionsDisponibles();

        IaResolved cfg = resolver.pour(clubId);
        if (!cfg.cleDisponible()) {
            // Au super-admin on indique l'écran : c'est lui qui peut agir, autant lui éviter la chasse.
            Utilisateur u = currentUser.current();
            String ou = (u != null && u.getRole() == Role.SUPER_ADMIN)
                    ? "Aucune clé pour le fournisseur « " + cfg.provider() + " » : pose-la dans "
                            + "Paramètres IA › Clés & modèles."
                    : "Contacte l'administrateur pour en configurer une pour ton club.";
            return new EtatChat(false, "PAS_DE_CLE",
                    nom + " a besoin d'une clé IA pour répondre. " + ou, nom, actions);
        }
        Integer reste = quota.resteAujourdhui(clubId, FEATURE, cfg);
        if (reste != null && reste <= 0) {
            return new EtatChat(false, "QUOTA_EPUISE",
                    "Limite de questions atteinte pour aujourd'hui. Réessaie demain, ou configure "
                            + "une clé IA propre à ton club pour lever la limite.", nom, actions);
        }
        return new EtatChat(true, null, null, nom, actions);
    }

    /**
     * Répond au dernier message du fil. L'historique fourni par le front est repris tel quel (borné),
     * précédé du contexte métier recalculé à chaque tour — c'est lui qui garantit des chiffres à jour.
     */
    public MessageChat repondre(List<MessageChat> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucun message.");
        }
        UUID clubId = clubCourant();
        String systeme = nomAssistant.appliquer(
                parametres.valeur(ParametreIaService.CLE_PROMPT_CHAT_PREPA), clubId);

        String texte = llm.genererTexte(clubId, FEATURE, systeme + "\n\n" + contexteAutorise(),
                conversation(messages), MAX_TOKENS);
        if (texte == null || texte.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Réponse vide du fournisseur IA.");
        }
        return new MessageChat("assistant", texte.trim());
    }

    // ── Assemblage du prompt ──

    /**
     * Concatène les blocs des {@link ContexteChat} auxquels l'utilisateur a droit. Un contexte qui
     * échoue est ignoré : le chat doit rester utilisable si une source est momentanément indisponible.
     */
    private String contexteAutorise() {
        StringBuilder sb = new StringBuilder("INDICATEURS DISPONIBLES (n'utilise QUE ces données) :\n");
        boolean vide = true;
        for (ContexteChat c : contextes) {
            if (!aPermission(c.permissionRequise())) continue;
            String bloc;
            try {
                bloc = c.bloc();
            } catch (RuntimeException e) {
                continue;
            }
            if (bloc == null || bloc.isBlank()) continue;
            sb.append("\n== ").append(c.libelle()).append(" ==\n").append(bloc.trim()).append("\n");
            vide = false;
        }
        if (vide) {
            sb.append("\nAucun indicateur disponible pour cet utilisateur : explique-le et invite-le à "
                    + "consulter les écrans de l'application.\n");
        }
        return sb.toString();
    }

    /** Aplatit le fil en un message utilisateur unique (les providers exposés ne prennent qu'un tour). */
    private String conversation(List<MessageChat> messages) {
        List<MessageChat> recents = messages.size() > MAX_HISTORIQUE
                ? messages.subList(messages.size() - MAX_HISTORIQUE, messages.size())
                : messages;
        if (recents.size() == 1) return texteDe(recents.get(0));

        StringJoiner sj = new StringJoiner("\n");
        sj.add("HISTORIQUE DE LA CONVERSATION :");
        for (int i = 0; i < recents.size() - 1; i++) {
            MessageChat m = recents.get(i);
            sj.add(("assistant".equalsIgnoreCase(m.role()) ? "Assistant : " : "Utilisateur : ") + texteDe(m));
        }
        sj.add("\nNOUVELLE QUESTION :");
        sj.add(texteDe(recents.get(recents.size() - 1)));
        return sj.toString();
    }

    private String texteDe(MessageChat m) {
        String c = m == null || m.contenu() == null ? "" : m.contenu().trim();
        if (c.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message vide.");
        return c.length() > 2000 ? c.substring(0, 2000) : c;
    }

    // ── Actions rapides : uniquement les cartes réellement accessibles ──

    private List<ActionRapide> actionsDisponibles() {
        List<ActionRapide> actions = new ArrayList<>();
        if (aPermission(Permission.PREPA_IA_BRIEFING)) {
            actions.add(new ActionRapide("briefing", "Briefing de la semaine",
                    "/api/assistant-ia/briefing", "POST"));
        }
        if (aPermission(Permission.PREPA_IA_DERIVES)) {
            actions.add(new ActionRapide("derives", "Dérives & surveillance",
                    "/api/assistant-ia/derives", "GET"));
        }
        if (aPermission(Permission.PREPA_IA_SIMULATION)) {
            actions.add(new ActionRapide("simulation", "Simuler une séance",
                    "/api/assistant-ia/simulation", "POST"));
        }
        return actions;
    }

    /**
     * L'utilisateur détient-il cette permission ? On interroge les autorités Spring, déjà filtrées
     * par le {@code PermissionResolver} : une permission dont le module add-on est coupé pour le club
     * en a été retirée. Tester l'autorité couvre donc à la fois le droit ET l'activation du module.
     */
    private boolean aPermission(Permission p) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        Utilisateur u = currentUser.current();
        if (u != null && u.getRole() == Role.SUPER_ADMIN) return true;
        for (GrantedAuthority a : auth.getAuthorities()) {
            if (p.getCode().equals(a.getAuthority())) return true;
        }
        return false;
    }

    private UUID clubCourant() {
        Utilisateur u = currentUser.current();
        if (u.getRole() == Role.SUPER_ADMIN) {
            ContexteActif ctx = ContexteActifHolder.get();
            return ctx != null ? ctx.clubId() : null;
        }
        return u.getClubId();
    }

    /** Exposé pour l'écran d'admin : le chat est-il bien câblé au catalogue des features IA ? */
    public static IaFeature feature() {
        return IaFeature.CHAT_PREPA;
    }
}
