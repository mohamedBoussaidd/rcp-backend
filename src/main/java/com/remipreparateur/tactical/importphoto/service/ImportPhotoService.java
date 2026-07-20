package com.remipreparateur.tactical.importphoto.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.remipreparateur.auth.entity.Role;
import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.auth.rbac.PermissionResolver;
import com.remipreparateur.performance.seance.repository.ReferentielDominanteRepository;
import com.remipreparateur.performance.seance.repository.ReferentielSousPrincipeRepository;
import com.remipreparateur.shared.security.CurrentUserProvider;
import com.remipreparateur.tactical.importphoto.dto.ImportPhotoDtos.*;
import com.remipreparateur.tactical.importphoto.entity.ImportPhotoJournal;
import com.remipreparateur.tactical.importphoto.repository.ClubParametreRepository;
import com.remipreparateur.tactical.importphoto.repository.ImportPhotoJournalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Import d'une séance/exercice depuis une photo : validation + compression de l'image,
 * appel DIRECT de l'API Anthropic (vision, SDK officiel — jamais depuis le front),
 * parsing STRICT du JSON (référentiels validés, coordonnées bornées), conversion du
 * schéma au format de l'éditeur Konva, quota par club/jour et journal d'audit.
 *
 * La clé API vient de l'environnement (ANTHROPIC_API_KEY), jamais du repo.
 * IMPORT_PHOTO_MOCK=true → réponse simulée (tests sans consommer l'API).
 */
@Service
public class ImportPhotoService {

    private static final Logger log = LoggerFactory.getLogger(ImportPhotoService.class);

    private static final long TAILLE_MAX_OCTETS = 10L * 1024 * 1024;   // 10 Mo
    // Testé à 2576px (résolution haute claude-opus-4-8) le 2026-07-20 : aucun gain net sur les
    // scènes denses (le modèle perd des éléments avant même de raisonner dessus — un problème de
    // perception, pas de résolution) pour ~3x le coût par appel. Revenu à 1568px : le résultat
    // reste un brouillon à corriger quel que soit le réglage, autant maîtriser le coût.
    private static final int LONG_COTE_MAX_PX = 1568;
    private static final String MODELE_VISION = "claude-opus-4-8";

    // Dimensions du terrain de l'éditeur Konva (schema-editor) pour convertir les 0..1.
    private static final int W_COMPLET = 1040, W_DEMI = 600, H_TERRAIN = 680;

    /** Les 5 palettes de jetons de l'éditeur : l'IA répond avec ces libellés, jamais des couleurs. */
    private static final Map<String, String> COULEURS_EQUIPE = Map.of(
            "mon_equipe", "#7c3aed",
            "equipe_1",   "#ef4444",
            "equipe_2",   "#eab308",
            "adversaire", "#1f2937",
            "joker",      "#f97316");
    private static final String COULEUR_EQUIPE_DEFAUT = "#7c3aed";

    /** Couleurs du matériel, alignées sur la palette « Équipement » de l'éditeur. */
    private static final Map<String, String> COULEURS_MATERIEL = Map.of(
            "plot",      "#ef4444",
            "coupelle",  "#f59e0b",
            "cerceau",   "#f97316",
            "mannequin", "#64748b",
            "echelle",   "#eab308",
            "haie",      "#f97316",
            "piquet",    "#22c55e");
    private static final String COULEUR_MATERIEL_DEFAUT = "#ffffff";   // ballon, mini-but

    /** Couleurs des formes d'annotation (palette de l'éditeur : rouge / jaune / bleu). */
    private static final Map<String, String> COULEURS_FORME = Map.of(
            "rouge", "#ef4444", "jaune", "#eab308", "bleu", "#2563eb");

    private static final Set<String> TYPES_ELEMENTS = Set.of(
            "joueur", "plot", "ballon", "but", "cerceau", "mannequin",
            "echelle", "haie", "piquet", "coupelle");
    private static final Set<String> TYPES_TRACES = Set.of("passe", "deplacement", "conduite", "tir");
    private static final Set<String> TYPES_FORMES = Set.of("rect", "ellipse", "losange", "triangle");

    /** Garde-fou : une série mal comprise par l'IA ne doit pas noyer le terrain. */
    private static final int SERIE_MAX = 40;

    private final ImportPhotoJournalRepository journalRepository;
    private final ClubParametreRepository clubParametreRepository;
    private final ParametreIaService parametres;
    private final ReferentielDominanteRepository dominanteRepository;
    private final ReferentielSousPrincipeRepository sousPrincipeRepository;
    private final CurrentUserProvider currentUser;
    private final PermissionResolver permissionResolver;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Path uploadDir;
    private final boolean mock;

    private volatile AnthropicClient client;   // construit paresseusement (clé requise)

    public ImportPhotoService(ImportPhotoJournalRepository journalRepository,
                              ClubParametreRepository clubParametreRepository,
                              ParametreIaService parametres,
                              ReferentielDominanteRepository dominanteRepository,
                              ReferentielSousPrincipeRepository sousPrincipeRepository,
                              CurrentUserProvider currentUser,
                              PermissionResolver permissionResolver,
                              @Value("${app.import-photo.upload-dir:./data/import-photos}") String uploadDir,
                              @Value("${app.import-photo.mock:#{environment.IMPORT_PHOTO_MOCK ?: 'false'}}") String mock) {
        this.journalRepository = journalRepository;
        this.clubParametreRepository = clubParametreRepository;
        this.parametres = parametres;
        this.dominanteRepository = dominanteRepository;
        this.sousPrincipeRepository = sousPrincipeRepository;
        this.currentUser = currentUser;
        this.permissionResolver = permissionResolver;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.mock = "true".equalsIgnoreCase(mock);
    }

    // ══════════ Point d'entrée ══════════

    public ImportPhotoResponse importer(MultipartFile photo) {
        Utilisateur u = currentUser.current();
        UUID clubId = permissionResolver.clubActif(u);
        if (clubId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Aucun club actif");
        }
        verifierQuota(clubId);

        byte[] jpeg = validerEtCompresser(photo);
        String photoPath = sauvegarderPhoto(jpeg);

        ImportPhotoJournal journal = new ImportPhotoJournal();
        journal.setClubId(clubId);
        journal.setUtilisateurId(u.getId());
        journal.setPhotoPath(photoPath);
        // Sauvé AVANT l'analyse : l'id généré part dans la réponse (pièce jointe), et
        // l'appel compte dans le quota même si l'analyse échoue ensuite (statut mis à jour).
        journal = journalRepository.save(journal);

        try {
            String brut = mock ? reponseMock() : appelerVision(jpeg);
            // Seule trace de ce que le modèle a réellement répondu : sans ce log, un élément
            // absent du rendu est indiscernable entre « le modèle ne l'a pas vu » et
            // « le modèle l'a proposé mais le parsing/la validation l'a rejeté silencieusement ».
            log.info("Import photo {} : réponse brute ({} car.) : {}", journal.getId(), brut.length(),
                    brut.length() > 8000 ? brut.substring(0, 8000) + "…" : brut);
            ImportPhotoResponse reponse = parser(brut, journal);
            journalRepository.save(journal);
            return reponse;
        } catch (ResponseStatusException e) {
            journal.setStatut("ERREUR");
            journal.setMessage(tronquer(e.getReason()));
            journalRepository.save(journal);
            throw e;
        } catch (Exception e) {
            log.error("Import photo : échec appel/parsing", e);
            journal.setStatut("ERREUR");
            journal.setMessage(tronquer(e.getMessage()));
            journalRepository.save(journal);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "L'analyse de la photo a échoué — réessaie dans un instant");
        }
    }

    /** Photo d'origine d'un import (affichage « pièce jointe » sur l'exercice). */
    public byte[] photo(UUID journalId) {
        ImportPhotoJournal j = journalRepository.findById(journalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import introuvable"));
        Utilisateur u = currentUser.current();
        UUID clubId = permissionResolver.clubActif(u);
        if (u.getRole() != Role.SUPER_ADMIN && (clubId == null || !clubId.equals(j.getClubId()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Import introuvable");
        }
        try {
            Path p = uploadDir.resolve(Paths.get(j.getPhotoPath()).getFileName().toString()).normalize();
            if (!p.startsWith(uploadDir) || !Files.exists(p)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo absente");
            }
            return Files.readAllBytes(p);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo illisible");
        }
    }

    /** Quota effectif d'un club (surcharge club sinon défaut global). */
    public int quotaDuClub(UUID clubId) {
        return clubParametreRepository.findByClubIdAndCle(clubId, "quota_import_photo")
                .map(p -> {
                    try { return Integer.parseInt(p.getValeur().trim()); }
                    catch (NumberFormatException e) { return null; }
                })
                .orElseGet(() -> parametres.valeurEntier(ParametreIaService.CLE_QUOTA_DEFAUT, 20));
    }

    /** Appels déjà consommés aujourd'hui par le club. */
    public long consommeAujourdhui(UUID clubId) {
        return journalRepository.countByClubIdAndCreatedAtAfter(clubId, LocalDate.now().atStartOfDay());
    }

    private void verifierQuota(UUID clubId) {
        int quota = quotaDuClub(clubId);
        if (consommeAujourdhui(clubId) >= quota) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Quota d'imports photo atteint pour aujourd'hui (" + quota + "/jour) — réessaie demain");
        }
    }

    // ══════════ Image : validation + compression ══════════

    private byte[] validerEtCompresser(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucune photo reçue");
        }
        if (photo.getSize() > TAILLE_MAX_OCTETS) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Photo trop lourde (max 10 Mo)");
        }
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(photo.getBytes()));
        } catch (IOException e) {
            image = null;
        }
        if (image == null) {
            // HEIC (iPhone) non décodable côté serveur : demander un JPEG (réglage « Plus compatible »).
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Format non pris en charge — envoie la photo en JPEG ou PNG (sur iPhone : Réglages → Appareil photo → Formats → « Plus compatible »)");
        }
        try {
            BufferedImage reduite = reduire(image);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(reduite, "jpg", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Photo illisible");
        }
    }

    /** Ramène le grand côté à 1568 px max (qualité vision suffisante, coût maîtrisé). */
    private BufferedImage reduire(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        int grand = Math.max(w, h);
        double ratio = grand > LONG_COTE_MAX_PX ? (double) LONG_COTE_MAX_PX / grand : 1.0;
        int nw = Math.max(1, (int) Math.round(w * ratio));
        int nh = Math.max(1, (int) Math.round(h * ratio));
        // TYPE_INT_RGB : aplatit aussi un éventuel canal alpha (PNG) pour l'encodage JPEG.
        BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return dst;
    }

    private String sauvegarderPhoto(byte[] jpeg) {
        try {
            Files.createDirectories(uploadDir);
            String nom = UUID.randomUUID() + ".jpg";
            Files.write(uploadDir.resolve(nom), jpeg);
            return nom;
        } catch (IOException e) {
            log.warn("Import photo : sauvegarde de la photo impossible", e);
            return null;   // non bloquant : l'analyse peut continuer sans pièce jointe
        }
    }

    // ══════════ Appel vision (SDK officiel Anthropic) ══════════

    private AnthropicClient clientAnthropic() {
        AnthropicClient c = client;
        if (c == null) {
            synchronized (this) {
                if (client == null) {
                    try {
                        client = AnthropicOkHttpClient.fromEnv();   // lit ANTHROPIC_API_KEY
                    } catch (Exception e) {
                        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                                "Import photo non configuré (clé API absente sur le serveur)");
                    }
                }
                c = client;
            }
        }
        return c;
    }

    private String appelerVision(byte[] jpeg) {
        String prompt = parametres.valeur(ParametreIaService.CLE_PROMPT_IMPORT_PHOTO);
        if (prompt == null || prompt.isBlank()) prompt = ParametreIaService.PROMPT_IMPORT_PHOTO_DEFAUT;

        // Pas de `temperature` : le paramètre est retiré sur claude-opus-4-8 (400 si envoyé).
        // La stabilité d'un import à l'autre passe donc par le prompt — d'où le champ « analyse »
        // qui impose de décrire la scène avant d'en produire les coordonnées.
        MessageCreateParams params = MessageCreateParams.builder()
                .model(MODELE_VISION)
                .maxTokens(8192L)
                .addUserMessageOfBlockParams(List.of(
                        ContentBlockParam.ofImage(ImageBlockParam.builder()
                                .source(Base64ImageSource.builder()
                                        .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
                                        .data(Base64.getEncoder().encodeToString(jpeg))
                                        .build())
                                .build()),
                        ContentBlockParam.ofText(TextBlockParam.builder().text(prompt).build())))
                .build();

        Message message = clientAnthropic().messages().create(params);
        return message.content().stream()
                .flatMap(b -> b.text().stream())
                .map(t -> t.text())
                .collect(Collectors.joining());
    }

    // ══════════ Parsing strict + conversion schéma ══════════

    private ImportPhotoResponse parser(String brut, ImportPhotoJournal journal) throws IOException {
        String json = extraireJson(brut);
        JsonNode racine = mapper.readTree(json);

        if (!racine.path("lisible").asBoolean(true)) {
            journal.setStatut("ILLISIBLE");
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Photo illisible ou sans rapport avec une séance — reprends-la de plus près, bien à plat");
        }

        JsonNode t = racine.path("texte");
        Set<String> codesDominantes = dominanteRepository.findAll().stream()
                .map(d -> d.getCode()).collect(Collectors.toSet());
        Set<String> codesSousPrincipes = sousPrincipeRepository.findAll().stream()
                .map(p -> p.getCode()).collect(Collectors.toSet());

        List<BlocExtrait> blocs = new ArrayList<>();
        for (JsonNode b : t.path("blocs")) {
            String libelle = texte(b, "libelle");
            if (libelle == null) continue;
            blocs.add(new BlocExtrait(libelle, entier(b, "dureeMinutes"),
                    texte(b, "sequencage"), texte(b, "consignes")));
        }

        JsonNode av = t.path("avance");
        AvanceExtrait avance = new AvanceExtrait(
                texte(av, "formatJoueurs"), decimal(av, "terrainLongueurM"), decimal(av, "terrainLargeurM"),
                texte(av, "sequencage"), texte(av, "butSystemeMarque"),
                texte(av, "reglesJeu"), texte(av, "variablesPedagogiques"));

        String type = texte(t, "type");
        TexteExtrait extrait = new TexteExtrait(
                "SEANCE".equals(type) || "EXERCICE".equals(type) ? type : null,
                texte(t, "titre"), texte(t, "description"), texte(t, "objectif"),
                entier(t, "dureeMinutes"), texte(t, "materiel"), blocs,
                codesValides(t.path("dominantes"), codesDominantes),
                codesValides(t.path("sousPrincipes"), codesSousPrincipes),
                avance);

        int[] compteurs = new int[2];
        String schemaJson = convertirSchema(racine.path("schema"), compteurs);

        return new ImportPhotoResponse(journal.getId(), extrait, schemaJson, compteurs[0], compteurs[1]);
    }

    /** Convertit le schéma normalisé 0..1 au format de l'éditeur (pixels, couleurs, ids). */
    private String convertirSchema(JsonNode schema, int[] compteurs) {
        if (schema.isMissingNode() || schema.isNull()) return null;
        String terrain = "demi".equals(schema.path("terrain").asText()) ? "demi" : "complet";
        int W = "demi".equals(terrain) ? W_DEMI : W_COMPLET;

        ObjectNode out = mapper.createObjectNode();
        out.put("terrain", terrain);
        ArrayNode elements = out.putArray("elements");
        ArrayNode traces = out.putArray("traces");
        ArrayNode formes = out.putArray("formes");

        Compteur n = new Compteur();
        for (JsonNode e : schema.path("elements")) {
            ajouterElement(elements, e.path("type").asText(), e, e, W, n);
        }
        // Séries : l'IA décrit « 8 plots de A à B » plutôt que d'énumérer 8 paires de coordonnées
        // (localisation et comptage étant ses points faibles) — on développe ici, régulièrement.
        for (JsonNode s : schema.path("series")) {
            String type = s.path("element").asText();
            JsonNode de = s.path("de"), a = s.path("a");
            int nombre = s.path("nombre").asInt(0);
            if (!TYPES_ELEMENTS.contains(type) || nombre < 2 || !estPoint(de) || !estPoint(a)) continue;
            nombre = Math.min(nombre, SERIE_MAX);
            double x1 = borne(de.get(0).asDouble()), y1 = borne(de.get(1).asDouble());
            double x2 = borne(a.get(0).asDouble()), y2 = borne(a.get(1).asDouble());
            for (int k = 0; k < nombre; k++) {
                double t = (double) k / (nombre - 1);
                ObjectNode point = mapper.createObjectNode();
                point.put("x", x1 + (x2 - x1) * t);
                point.put("y", y1 + (y2 - y1) * t);
                ajouterElement(elements, type, point, s, W, n);
            }
        }
        for (JsonNode tr : schema.path("traces")) {
            String type = tr.path("type").asText();
            JsonNode pts = tr.path("points");
            if (!TYPES_TRACES.contains(type) || !pts.isArray() || pts.size() < 4 || pts.size() % 2 != 0) continue;
            ObjectNode trace = traces.addObject();
            trace.put("id", "imp-t-" + (++n.traces));
            trace.put("type", type);
            ArrayNode points = trace.putArray("points");
            for (int k = 0; k < pts.size(); k += 2) {
                points.add(Math.round(borne(pts.get(k).asDouble()) * W));
                points.add(Math.round(borne(pts.get(k + 1).asDouble()) * H_TERRAIN));
            }
        }
        // Formes d'annotation : zones de jeu (carré de conservation, couloirs…) que l'éditeur sait
        // déjà dessiner — sans elles, toute délimitation tracée sur la fiche était perdue.
        for (JsonNode f : schema.path("formes")) {
            String type = f.path("type").asText();
            if (!TYPES_FORMES.contains(type)) continue;
            if (!f.hasNonNull("x") || !f.hasNonNull("y")) continue;
            double x = borne(f.path("x").asDouble()), y = borne(f.path("y").asDouble());
            double w = borne(f.path("w").asDouble(0)), h = borne(f.path("h").asDouble(0));
            if (w <= 0 || h <= 0) continue;
            ObjectNode forme = formes.addObject();
            forme.put("id", "imp-f-" + (++n.formes));
            forme.put("type", type);
            forme.put("x", Math.round(x * W));
            forme.put("y", Math.round(y * H_TERRAIN));
            forme.put("w", Math.max(12, Math.round(w * W)));
            forme.put("h", Math.max(12, Math.round(h * H_TERRAIN)));
            forme.put("couleur", COULEURS_FORME.getOrDefault(f.path("couleur").asText("jaune"), "#eab308"));
            String texte = texte(f, "texte");
            if (texte != null) forme.put("texte", texte);
        }

        compteurs[0] = n.elements + n.formes;
        compteurs[1] = n.traces;
        return n.elements == 0 && n.traces == 0 && n.formes == 0 ? null : out.toString();
    }

    /** Compteurs d'ids du schéma converti (un par famille, pour des ids stables et lisibles). */
    private static final class Compteur { int elements, traces, formes; }

    private static boolean estPoint(JsonNode n) {
        return n.isArray() && n.size() == 2 && n.get(0).isNumber() && n.get(1).isNumber();
    }

    /**
     * Ajoute un élément : `position` porte x/y (0..1), `attributs` la couleur/le numéro/la rotation
     * — deux nœuds distincts pour qu'une série applique ses attributs à chacun de ses points.
     */
    private void ajouterElement(ArrayNode cible, String type, JsonNode position, JsonNode attributs,
                                int W, Compteur n) {
        if (!TYPES_ELEMENTS.contains(type)) return;
        ObjectNode el = cible.addObject();
        el.put("id", "imp-" + (++n.elements));
        el.put("type", type);
        el.put("x", Math.round(borne(position.path("x").asDouble(0.5)) * W));
        el.put("y", Math.round(borne(position.path("y").asDouble(0.5)) * H_TERRAIN));
        if ("joueur".equals(type)) {
            el.put("couleur", COULEURS_EQUIPE.getOrDefault(
                    attributs.path("couleur").asText(""), COULEUR_EQUIPE_DEFAUT));
            if (attributs.hasNonNull("numero")) el.put("numero", attributs.path("numero").asInt());
            else el.put("numero", n.elements);
        } else {
            el.put("couleur", COULEURS_MATERIEL.getOrDefault(type, COULEUR_MATERIEL_DEFAUT));
        }
        // Orientation : n'a de sens que pour le matériel allongé (échelle, haie, mini-but…).
        int rotation = ((attributs.path("rotation").asInt(0) % 360) + 360) % 360;
        if (rotation != 0) el.put("rotation", rotation);
    }

    /** Le modèle peut entourer le JSON de texte/balises malgré la consigne : on isole l'objet. */
    private String extraireJson(String brut) {
        String s = brut.trim();
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```\\s*$", "");
        }
        int debut = s.indexOf('{');
        int fin = s.lastIndexOf('}');
        if (debut < 0 || fin <= debut) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Réponse IA invalide (pas de JSON)");
        }
        return s.substring(debut, fin + 1);
    }

    private static double borne(double v) { return Math.max(0, Math.min(1, v)); }

    private static String texte(JsonNode n, String champ) {
        JsonNode v = n.path(champ);
        if (v.isMissingNode() || v.isNull()) return null;
        String s = v.asText().trim();
        return s.isEmpty() || "null".equals(s) ? null : s;
    }

    private static Integer entier(JsonNode n, String champ) {
        JsonNode v = n.path(champ);
        return v.isNumber() ? v.asInt() : null;
    }

    private static BigDecimal decimal(JsonNode n, String champ) {
        JsonNode v = n.path(champ);
        return v.isNumber() ? BigDecimal.valueOf(v.asDouble()) : null;
    }

    private static List<String> codesValides(JsonNode tableau, Set<String> valides) {
        List<String> out = new ArrayList<>();
        if (tableau.isArray()) {
            for (JsonNode c : tableau) {
                String code = c.asText();
                if (valides.contains(code) && !out.contains(code)) out.add(code);
            }
        }
        return out;
    }

    private static String tronquer(String s) {
        if (s == null) return null;
        return s.length() > 480 ? s.substring(0, 480) : s;
    }

    /** Réponse simulée (IMPORT_PHOTO_MOCK=true) : valide tout le pipeline sans appel réel. */
    private String reponseMock() {
        return """
        {"lisible":true,
         "texte":{"type":"EXERCICE","titre":"Conservation 4v4 + 2 jokers","description":"Conserver le ballon, jouer avec les appuis",
           "objectif":"Conservation sous pression","dureeMinutes":12,"materiel":"8 chasubles, 6 plots",
           "blocs":[{"libelle":"Mise en place","dureeMinutes":3,"sequencage":null,"consignes":"Deux équipes de 4"},
                    {"libelle":"Jeu","dureeMinutes":9,"sequencage":"3 × 3'","consignes":"Jokers offensifs"}],
           "dominantes":["technique","tactique"],"sousPrincipes":["conservation"],
           "avance":{"formatJoueurs":"4 vs 4 + 2 jokers","terrainLongueurM":25,"terrainLargeurM":20,
                     "sequencage":"3 × 3'","butSystemeMarque":"10 passes = 1 pt","reglesJeu":"2 touches max","variablesPedagogiques":"Ajouter un gardien"}},
         "schema":{"terrain":"demi",
           "elements":[{"type":"joueur","couleur":"mon_equipe","numero":1,"x":0.3,"y":0.3},
                       {"type":"joueur","couleur":"mon_equipe","numero":2,"x":0.6,"y":0.35},
                       {"type":"joueur","couleur":"adversaire","numero":1,"x":0.45,"y":0.5},
                       {"type":"joueur","couleur":"joker","numero":9,"x":0.15,"y":0.5},
                       {"type":"ballon","numero":null,"x":0.32,"y":0.33},
                       {"type":"echelle","rotation":90,"x":0.8,"y":0.4}],
           "series":[{"element":"plot","de":[0.2,0.2],"a":[0.2,0.8],"nombre":5}],
           "formes":[{"type":"rect","x":0.15,"y":0.15,"w":0.5,"h":0.6,"couleur":"jaune","texte":"25 × 20 m"}],
           "traces":[{"type":"passe","points":[0.32,0.33,0.6,0.35]}]}}
        """;
    }
}
