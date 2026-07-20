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
    private static final int LONG_COTE_MAX_PX = 1568;                  // guidance vision (coût maîtrisé)
    private static final String MODELE_VISION = "claude-opus-4-8";

    // Dimensions du terrain de l'éditeur Konva (schema-editor) pour convertir les 0..1.
    private static final int W_COMPLET = 1040, W_DEMI = 600, H_TERRAIN = 680;
    private static final String COULEUR_NOUS = "#7c3aed", COULEUR_ADVERSE = "#1f2937";

    private static final Set<String> TYPES_ELEMENTS =
            Set.of("joueur", "plot", "ballon", "but", "cerceau", "mannequin");
    private static final Set<String> TYPES_TRACES = Set.of("passe", "deplacement", "conduite", "tir");

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

        int i = 0;
        for (JsonNode e : schema.path("elements")) {
            String type = e.path("type").asText();
            if (!TYPES_ELEMENTS.contains(type)) continue;
            ObjectNode el = elements.addObject();
            el.put("id", "imp-" + (++i));
            el.put("type", type);
            el.put("x", Math.round(borne(e.path("x").asDouble(0.5)) * W));
            el.put("y", Math.round(borne(e.path("y").asDouble(0.5)) * H_TERRAIN));
            String couleurLogique = e.path("couleur").asText("neutre");
            if ("joueur".equals(type)) {
                el.put("couleur", "adverse".equals(couleurLogique) ? COULEUR_ADVERSE : COULEUR_NOUS);
                if (e.hasNonNull("numero")) el.put("numero", e.path("numero").asInt());
                else el.put("numero", i);
            } else {
                el.put("couleur", switch (type) {
                    case "plot" -> "#ef4444";
                    case "cerceau" -> "#eab308";
                    case "mannequin" -> "#f59e0b";
                    default -> "#ffffff";
                });
            }
        }
        int j = 0;
        for (JsonNode tr : schema.path("traces")) {
            String type = tr.path("type").asText();
            JsonNode pts = tr.path("points");
            if (!TYPES_TRACES.contains(type) || !pts.isArray() || pts.size() < 4 || pts.size() % 2 != 0) continue;
            ObjectNode trace = traces.addObject();
            trace.put("id", "imp-t-" + (++j));
            trace.put("type", type);
            ArrayNode points = trace.putArray("points");
            for (int k = 0; k < pts.size(); k += 2) {
                points.add(Math.round(borne(pts.get(k).asDouble()) * W));
                points.add(Math.round(borne(pts.get(k + 1).asDouble()) * H_TERRAIN));
            }
        }
        compteurs[0] = i;
        compteurs[1] = j;
        return i == 0 && j == 0 ? null : out.toString();
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
           "elements":[{"type":"joueur","couleur":"nous","numero":1,"x":0.3,"y":0.3},
                       {"type":"joueur","couleur":"nous","numero":2,"x":0.6,"y":0.35},
                       {"type":"joueur","couleur":"adverse","numero":1,"x":0.45,"y":0.5},
                       {"type":"ballon","couleur":"neutre","numero":null,"x":0.32,"y":0.33},
                       {"type":"plot","couleur":"neutre","numero":null,"x":0.2,"y":0.2}],
           "traces":[{"type":"passe","points":[0.32,0.33,0.6,0.35]}]}}
        """;
    }
}
