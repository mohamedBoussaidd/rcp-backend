package com.remipreparateur.performance.seance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remipreparateur.auth.entity.Role;
import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.ia.service.LlmService;
import com.remipreparateur.performance.seance.entity.ReferentielSousPrincipe;
import com.remipreparateur.performance.seance.entity.TypeSeance;
import com.remipreparateur.performance.seance.repository.ReferentielSousPrincipeRepository;
import com.remipreparateur.performance.seance.repository.TypeSeanceRepository;
import com.remipreparateur.shared.security.ContexteActif;
import com.remipreparateur.shared.security.ContexteActifHolder;
import com.remipreparateur.shared.security.CurrentUserProvider;
import com.remipreparateur.tactical.exercice.entity.Exercice;
import com.remipreparateur.tactical.exercice.repository.ExerciceRepository;
import com.remipreparateur.tactical.importphoto.service.ParametreIaService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * Générateur de séance par IA (C4) : à partir d'une demande en langage naturel, l'IA compose un
 * BROUILLON de séance en réutilisant en priorité les exercices du club (puis les globaux), et en
 * décrivant en texte ce qu'elle ne trouve pas. Passe par le socle IA (résolution clé/quota/provider).
 * Ne crée jamais de séance : renvoie un brouillon que le coach valide et ajuste dans l'éditeur.
 */
@Service
public class SeanceGenerationService {

    /** Clé du prompt éditable — source unique du texte par défaut : {@link ParametreIaService}. */
    public static final String CLE_PROMPT = ParametreIaService.CLE_PROMPT_GENERATEUR_SEANCE;
    private static final String FEATURE = "generateur_seance";
    /** Borne le nombre d'exercices envoyés au LLM (club prioritaire) pour maîtriser les tokens. */
    private static final int MAX_CATALOGUE = 200;

    private final LlmService llm;
    private final ExerciceRepository exerciceRepository;
    private final TypeSeanceRepository typeSeanceRepository;
    private final ReferentielSousPrincipeRepository sousPrincipeRepository;
    private final ParametreIaService parametres;
    private final CurrentUserProvider currentUser;
    private final ObjectMapper mapper = new ObjectMapper();

    public SeanceGenerationService(LlmService llm, ExerciceRepository exerciceRepository,
                                   TypeSeanceRepository typeSeanceRepository,
                                   ReferentielSousPrincipeRepository sousPrincipeRepository,
                                   ParametreIaService parametres, CurrentUserProvider currentUser) {
        this.llm = llm;
        this.exerciceRepository = exerciceRepository;
        this.typeSeanceRepository = typeSeanceRepository;
        this.sousPrincipeRepository = sousPrincipeRepository;
        this.parametres = parametres;
        this.currentUser = currentUser;
    }

    // ── DTOs de brouillon (consommés par le front pour pré-remplir l'éditeur) ──
    public record BlocBrouillon(String libelle, Integer dureeMinutes, String sequencage, List<UUID> exerciceIds) {}
    public record ExerciceLibre(String nom, String description) {}
    public record Dosages(Short tactiqueOrg, Short tactiqueFonc, Short technique, Short mental, Short athletique) {}
    public record SeanceBrouillon(String titre, UUID typeSeanceId, String typeLibelle, Integer dureeMinutes,
                                  String objectif, Dosages dominantes, List<BlocBrouillon> blocs,
                                  List<ExerciceLibre> exercicesManquants, String note) {}

    public SeanceBrouillon generer(String demande) {
        if (demande == null || demande.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Décris la séance souhaitée");
        }
        Utilisateur u = currentUser.current();
        UUID clubId = clubCourant(u);

        List<Exercice> catalogue = new ArrayList<>();
        if (clubId != null) catalogue.addAll(exerciceRepository.findByClubIdOrderByCreatedAtDesc(clubId));
        catalogue.addAll(exerciceRepository.findByClubIdIsNullOrderByCreatedAtDesc());
        Map<String, Exercice> parId = new LinkedHashMap<>();
        catalogue.forEach(e -> parId.put(e.getId().toString(), e));

        List<TypeSeance> types = typeSeanceRepository.findAll();

        String systeme = construireSysteme(catalogue, types);
        String reponse = llm.genererTexte(clubId, FEATURE, systeme, demande.trim(), 4096);
        return parser(reponse, parId, types);
    }

    // ── Prompt ──

    private String construireSysteme(List<Exercice> catalogue, List<TypeSeance> types) {
        String base = parametres.valeur(CLE_PROMPT);
        if (base == null || base.isBlank()) base = ParametreIaService.PROMPT_GENERATEUR_SEANCE_DEFAUT;

        // Référentiel des thèmes de jeu (id → libellé), résolu une fois pour tout le catalogue.
        Map<UUID, String> themes = new HashMap<>();
        for (ReferentielSousPrincipe sp : sousPrincipeRepository.findAll()) themes.put(sp.getId(), sp.getLibelle());

        StringBuilder cat = new StringBuilder();
        int n = 0;
        for (Exercice e : catalogue) {
            if (n++ >= MAX_CATALOGUE) break;
            cat.append("- ").append(e.getId()).append(" | ").append(ligneTags(e, themes)).append("\n");
        }
        if (catalogue.isEmpty()) cat.append("(aucun exercice en bibliothèque — décris-les tous en texte)\n");
        else if (catalogue.size() > MAX_CATALOGUE) {
            cat.append("(… catalogue tronqué à ").append(MAX_CATALOGUE).append(" exercices)\n");
        }

        StringBuilder tl = new StringBuilder();
        for (TypeSeance t : types) tl.append("- ").append(orVide(t.getCode())).append(" : ").append(orVide(t.getLibelle())).append("\n");

        return base
                + "\n\nTYPES DE SÉANCE AUTORISÉS (utilise le code exact) :\n" + tl
                + "\nLÉGENDE DES TAGS D'EXERCICE : forme/type = nature de l'exercice · int = intensité sur 5 · "
                + "durée en minutes · dom = dominantes dosées (axe score/5, axes : tactiqueOrg, tactiqueFonc, "
                + "technique, mental, athletique) · thèmes = thèmes de jeu travaillés · obj = objectif.\n"
                + "\nBIBLIOTHÈQUE D'EXERCICES DISPONIBLES (réutilise leur id EXACT quand ça colle) :\n" + cat;
    }

    /** Ligne compacte et dense en tags pour un exercice (nom + forme/type/intensité/durée + dominantes + thèmes + objectif). */
    private String ligneTags(Exercice e, Map<UUID, String> themes) {
        StringJoiner sj = new StringJoiner(" | ");
        sj.add(orVide(e.getNom()));

        StringJoiner meta = new StringJoiner(" ");
        if (e.getForme() != null) meta.add("forme=" + e.getForme());
        meta.add("type=" + orVide(e.getType()));
        if (e.getIntensite() != null) meta.add("int=" + e.getIntensite());
        if (e.getDureeMinutes() != null) meta.add(e.getDureeMinutes() + "min");
        sj.add(meta.toString());

        String dom = dominantes(e);
        if (!dom.isBlank()) sj.add("dom: " + dom);

        if (e.getSousPrincipeIds() != null && !e.getSousPrincipeIds().isEmpty()) {
            StringJoiner th = new StringJoiner(", ");
            for (UUID id : e.getSousPrincipeIds()) {
                String lib = themes.get(id);
                if (lib != null) th.add(lib);
            }
            if (th.length() > 0) sj.add("thèmes: " + th);
        }

        if (e.getObjectif() != null && !e.getObjectif().isBlank()) sj.add("obj: " + e.getObjectif().trim());
        return sj.toString();
    }

    /** Dominantes dosées non nulles, du plus fort au plus faible : « technique 4, tactiqueFonc 3 ». */
    private String dominantes(Exercice e) {
        record Axe(String nom, Short score) {}
        List<Axe> axes = new ArrayList<>(List.of(
                new Axe("tactiqueOrg", e.getDominanteTactiqueOrgIntensite()),
                new Axe("tactiqueFonc", e.getDominanteTactiqueFoncIntensite()),
                new Axe("technique", e.getDominanteTechniqueIntensite()),
                new Axe("mental", e.getDominanteMentalIntensite()),
                new Axe("athletique", e.getDominanteAthletiqueIntensite())));
        StringJoiner sj = new StringJoiner(", ");
        axes.stream()
                .filter(a -> a.score() != null && a.score() > 0)
                .sorted((a, b) -> Short.compare(b.score(), a.score()))
                .forEach(a -> sj.add(a.nom() + " " + a.score()));
        return sj.toString();
    }

    // ── Parsing ──

    private SeanceBrouillon parser(String brut, Map<String, Exercice> parId, List<TypeSeance> types) {
        try {
            JsonNode r = mapper.readTree(extraireJson(brut));
            String titre = texte(r, "titre");
            Integer duree = entier(r, "dureeMinutes");
            String objectif = texte(r, "objectif");

            String typeCode = texte(r, "typeCode");
            TypeSeance type = types.stream()
                    .filter(t -> typeCode != null && typeCode.equalsIgnoreCase(t.getCode()))
                    .findFirst().orElse(types.isEmpty() ? null : types.get(0));

            JsonNode d = r.path("dominantes");
            Dosages dominantes = new Dosages(dosage(d, "tactiqueOrg"), dosage(d, "tactiqueFonc"),
                    dosage(d, "technique"), dosage(d, "mental"), dosage(d, "athletique"));

            List<BlocBrouillon> blocs = new ArrayList<>();
            List<ExerciceLibre> manquants = new ArrayList<>();
            for (JsonNode b : r.path("blocs")) {
                List<UUID> ids = new ArrayList<>();
                for (JsonNode ex : b.path("exercices")) {
                    if (ex.isTextual()) {
                        Exercice e = parId.get(ex.asText());
                        if (e != null) ids.add(e.getId());
                        else manquants.add(new ExerciceLibre(ex.asText(), null));
                    } else if (ex.isObject()) {
                        manquants.add(new ExerciceLibre(texte(ex, "nom"), texte(ex, "description")));
                    }
                }
                blocs.add(new BlocBrouillon(texte(b, "libelle"), entier(b, "dureeMinutes"),
                        texte(b, "sequencage"), ids));
            }

            return new SeanceBrouillon(titre, type != null ? type.getId() : null,
                    type != null ? type.getLibelle() : null, duree, objectif, dominantes,
                    blocs, manquants, texte(r, "note"));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Réponse IA non exploitable — reformule ta demande.");
        }
    }

    private String extraireJson(String s) {
        if (s == null) return "{}";
        int a = s.indexOf('{'), b = s.lastIndexOf('}');
        return (a >= 0 && b > a) ? s.substring(a, b + 1) : s;
    }

    private static String texte(JsonNode n, String champ) {
        JsonNode v = n.path(champ);
        return v.isMissingNode() || v.isNull() || !v.isValueNode() ? null : v.asText();
    }

    private static Integer entier(JsonNode n, String champ) {
        JsonNode v = n.path(champ);
        return v.isInt() || v.isLong() ? v.asInt() : (v.isTextual() && v.asText().matches("\\d+") ? Integer.parseInt(v.asText()) : null);
    }

    private static Short dosage(JsonNode n, String champ) {
        JsonNode v = n.path(champ);
        if (!v.isNumber()) return null;
        return (short) Math.max(0, Math.min(5, v.asInt()));
    }

    private UUID clubCourant(Utilisateur u) {
        if (u.getRole() == Role.SUPER_ADMIN) {
            ContexteActif ctx = ContexteActifHolder.get();
            return ctx != null ? ctx.clubId() : null;
        }
        return u.getClubId();
    }

    private static String orVide(String s) { return s == null ? "" : s; }
}
