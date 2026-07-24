package com.remipreparateur.tactical.importphoto.service;

import com.remipreparateur.tactical.importphoto.entity.ParametreIa;
import com.remipreparateur.tactical.importphoto.entity.ParametreIaHistorique;
import com.remipreparateur.tactical.importphoto.repository.ParametreIaHistoriqueRepository;
import com.remipreparateur.tactical.importphoto.repository.ParametreIaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Paramètres IA globaux (clé/valeur) : lecture avec cache court (60 s), édition
 * super-admin avec HISTORIQUE des versions et restauration. Le prompt vision de
 * l'import photo a un défaut EN DUR ici (fallback si la clé est absente en base).
 */
@Service
public class ParametreIaService {

    public static final String CLE_PROMPT_IMPORT_PHOTO = "prompt_import_photo";
    public static final String CLE_PROMPT_GENERATEUR_SEANCE = "prompt_generateur_seance";
    public static final String CLE_QUOTA_DEFAUT = "quota_import_photo_defaut";

    private static final long CACHE_MS = 60_000;

    private final ParametreIaRepository repository;
    private final ParametreIaHistoriqueRepository historiqueRepository;

    private record Cache(String valeur, long expiration) {}
    private final Map<String, Cache> cache = new ConcurrentHashMap<>();

    public ParametreIaService(ParametreIaRepository repository,
                              ParametreIaHistoriqueRepository historiqueRepository) {
        this.repository = repository;
        this.historiqueRepository = historiqueRepository;
    }

    /** Valeur courante (cache 60 s), sinon défaut en dur. */
    public String valeur(String cle) {
        Cache c = cache.get(cle);
        long now = System.currentTimeMillis();
        if (c != null && c.expiration() > now) return c.valeur();
        String v = repository.findById(cle).map(ParametreIa::getValeur).orElseGet(() -> defaut(cle));
        cache.put(cle, new Cache(v, now + CACHE_MS));
        return v;
    }

    public int valeurEntier(String cle, int fallback) {
        try { return Integer.parseInt(valeur(cle).trim()); } catch (Exception e) { return fallback; }
    }

    /** Valeur stockée telle quelle (édition admin) — sans fallback caché. */
    public String valeurBrute(String cle) {
        return repository.findById(cle).map(ParametreIa::getValeur).orElseGet(() -> defaut(cle));
    }

    public List<ParametreIaHistorique> historique(String cle) {
        return historiqueRepository.findTop20ByCleOrderByCreatedAtDesc(cle);
    }

    /** Met à jour la valeur en historisant la version précédente. */
    @Transactional
    public void mettreAJour(String cle, String valeur, UUID par) {
        if (valeur == null || valeur.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valeur vide");
        }
        repository.findById(cle).ifPresent(p -> {
            ParametreIaHistorique h = new ParametreIaHistorique();
            h.setCle(cle);
            h.setValeur(p.getValeur());
            h.setCreatedBy(p.getUpdatedBy());
            h.setCreatedAt(p.getUpdatedAt());
            historiqueRepository.save(h);
        });
        ParametreIa p = repository.findById(cle).orElseGet(() -> {
            ParametreIa n = new ParametreIa();
            n.setCle(cle);
            return n;
        });
        p.setValeur(valeur);
        p.setUpdatedAt(LocalDateTime.now());
        p.setUpdatedBy(par);
        repository.save(p);
        cache.remove(cle);
    }

    @Transactional
    public void restaurer(String cle, UUID historiqueId, UUID par) {
        ParametreIaHistorique h = historiqueRepository.findById(historiqueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Version introuvable"));
        if (!h.getCle().equals(cle)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Version d'une autre clé");
        }
        mettreAJour(cle, h.getValeur(), par);
    }

    /** Défauts en dur (source unique) — utilisés si la clé n'existe pas en base. */
    public String defaut(String cle) {
        return switch (cle) {
            case CLE_QUOTA_DEFAUT -> "20";
            case CLE_PROMPT_IMPORT_PHOTO -> PROMPT_IMPORT_PHOTO_DEFAUT;
            case CLE_PROMPT_GENERATEUR_SEANCE -> PROMPT_GENERATEUR_SEANCE_DEFAUT;
            default -> "";
        };
    }

    /**
     * Prompt par défaut du générateur de séance IA (C4), éditable par le super-admin (écran
     * Paramètres IA) — ce texte n'est que le fallback si la clé est absente en base.
     *
     * <p>Le catalogue d'exercices et sa légende de tags (dominantes, thèmes, intensité, durée)
     * sont ajoutés automatiquement par {@code SeanceGenerationService} APRÈS ce prompt : inutile
     * de les décrire ici, mais les consignes ci-dessous s'appuient dessus pour guider la sélection.</p>
     */
    public static final String PROMPT_GENERATEUR_SEANCE_DEFAUT = """
Tu es préparateur/entraîneur de football. À partir de la demande du coach, compose une séance d'entraînement.

Réponds UNIQUEMENT avec un objet JSON valide, sans texte ni balises autour. Contrat :
{
  "titre": "court",
  "typeCode": "<un des codes autorisés>",
  "dureeMinutes": nombre,
  "objectif": "1 phrase",
  "dominantes": { "tactiqueOrg":0-5, "tactiqueFonc":0-5, "technique":0-5, "mental":0-5, "athletique":0-5 },
  "blocs": [
    { "libelle":"Échauffement", "dureeMinutes":nombre, "sequencage":"ex: 3x4'",
      "exercices": [ "<id d'un exercice existant>", { "nom":"...", "description":"consignes" } ] }
  ],
  "note": "précision facultative pour le coach"
}

Règles :
- Réutilise EN PRIORITÉ les exercices de la bibliothèque via leur id exact. N'invente jamais d'id.
- Choisis les exercices dont les TAGS collent à la demande : privilégie ceux dont les dominantes (dom)
  et les thèmes de jeu correspondent à l'intention, et dont l'intensité et la forme conviennent au
  moment visé (échauffement, corps de séance, retour au calme).
- Garde les "dominantes" de la séance COHÉRENTES avec les exercices retenus (une séance à dominante
  technique enchaîne des exercices marqués technique).
- Pour un exercice absent de la bibliothèque, mets un objet { "nom", "description" } (le coach le créera).
- dominantes : dose chaque axe de 0 (pas travaillé) à 5 (dominant), cohérent avec la demande.
- 2 à 4 blocs, durées réalistes, cohérentes avec dureeMinutes.
""";

    /**
     * Prompt vision par défaut de l'import photo. Énumère explicitement la palette du
     * schéma, les référentiels du mode avancé (codes V61) et le contrat JSON strict.
     * Éditable par le super-admin (écran Paramètres IA) — ce texte n'est que le fallback.
     *
     * Trois partis pris, tirés des écarts constatés sur des fiches réelles :
     *  · un champ `analyse` OBLIGE à décrire la scène avant de produire des coordonnées
     *    (la localisation spatiale se dégrade nettement sans cette étape) ;
     *  · les objets répétitifs passent par `series` (« 8 plots de A à B ») plutôt que par une
     *    énumération — le comptage et l'espacement sont des faiblesses connues de la vision ;
     *  · les délimitations tracées deviennent des `formes`, que l'éditeur sait déjà dessiner.
     */
    public static final String PROMPT_IMPORT_PHOTO_DEFAUT = """
Tu analyses la photo d'une fiche de séance ou d'exercice de football dessinée sur papier par un entraîneur (texte manuscrit ou imprimé + éventuel schéma tactique).

Réponds UNIQUEMENT avec un objet JSON valide, sans aucun texte autour, sans balises markdown. Contrat exact :

{
  "lisible": true,
  "analyse": "2 à 4 phrases décrivant la scène AVANT de la coder : terrain ou zone dessinée, orientation, combien d'équipes et à quoi tu les reconnais, matériel présent et comment il est disposé (en ligne ? en colonne ?), flèches visibles",
  "texte": {
    "type": "SEANCE" ou "EXERCICE" (ton meilleur jugement : plusieurs temps/blocs = SEANCE, un seul jeu = EXERCICE),
    "titre": "..." ou null,
    "description": "consignes générales lisibles sur la fiche" ou null,
    "objectif": "..." ou null,
    "dureeMinutes": nombre ou null,
    "materiel": "..." ou null,
    "blocs": [ { "libelle": "...", "dureeMinutes": nombre ou null, "sequencage": "ex : 2 × (4 × 1' + 1')" ou null, "consignes": "..." ou null } ],
    "dominantes": [codes parmi : "technique","tactique","physique","musculaire","recuperation","mental","cpa","specifique","recup_aerobie","vivacite","puissance_aerobie","vitesse","force_specifique"],
    "sousPrincipes": [codes parmi : "sortie_de_balle","conservation","progression","desequilibre","finition","defendre_en_zone","pressing","bloc","proteger_le_but","contre_attaque","conservation_post_recup","contre_pressing","repli_defensif","cpa_offensifs","cpa_defensifs"],
    "avance": {
      "formatJoueurs": "ex : 4 vs 4 + 2 jokers" ou null,
      "terrainLongueurM": nombre ou null,
      "terrainLargeurM": nombre ou null,
      "sequencage": "..." ou null,
      "butSystemeMarque": "..." ou null,
      "reglesJeu": "..." ou null,
      "variablesPedagogiques": "..." ou null
    }
  },
  "schema": {
    "terrain": "complet" ou "demi",
    "elements": [ { "type": <type>, "couleur": <equipe>, "numero": nombre ou null, "rotation": degrés ou null, "x": 0.0-1.0, "y": 0.0-1.0 } ],
    "series": [ { "element": <type>, "de": [x, y], "a": [x, y], "nombre": nombre, "rotation": degrés ou null } ],
    "formes": [ { "type": "rect"|"ellipse"|"losange"|"triangle", "x": 0.0-1.0, "y": 0.0-1.0, "w": 0.0-1.0, "h": 0.0-1.0, "couleur": "rouge"|"jaune"|"bleu", "texte": "..." ou null } ],
    "traces": [ { "type": "passe"|"deplacement"|"conduite"|"tir", "points": [x1, y1, x2, y2, ...] } ]
  }
}

<type> vaut : "joueur", "ballon", "plot" (cône), "coupelle" (soucoupe plate), "cerceau",
"but" (cage ou porte, quelle que soit sa taille), "mannequin", "echelle" (échelle de rythme),
"haie", "piquet" (jalon de slalom).

<equipe> vaut, pour les joueurs UNIQUEMENT :
- "mon_equipe" : l'équipe dont le point de vue est celui de la fiche (celle qui attaque, celle
  qui a le ballon, ou simplement la première couleur si rien ne les distingue) ;
- "equipe_1", "equipe_2" : les autres équipes quand l'exercice en oppose trois ou plus
  (jeu de position à 3 couleurs, ateliers en rotation…) ;
- "adversaire" : les défenseurs / l'équipe adverse d'un exercice à deux camps ;
- "joker" : les joueurs neutres qui jouent avec l'équipe en possession (jokers, appuis, relais).
Ce champ est ignoré pour tout ce qui n'est pas un joueur.

MÉTHODE — remplis "analyse" AVANT tout le reste, puis produis des coordonnées cohérentes
avec ce que tu viens d'y décrire :
1. Combien de buts vois-tu ? Deux buts opposés → "complet". Un seul but, ou une zone carrée
   sans but → "demi". Un demi-terrain vu de loin reste "demi".
2. Le dessin est-il vu de face (vue de dessus) ou en biais/perspective ? S'il est en biais,
   REDRESSE mentalement : x et y décrivent le terrain vu de dessus, pas l'image.
3. Le schéma occupe-t-il toute l'image ou seulement une partie (fiche avec du texte autour) ?
   x et y sont relatifs au RECTANGLE DU TERRAIN, pas à la photo : le coin du terrain vaut
   (0,0) même s'il est au milieu de l'image.
4. Quels objets se répètent en ligne, en colonne ou en grille ? Chacun de ces groupes va dans
   "series", jamais dans "elements".
5. Quelles flèches sont dessinées ? CHAQUE flèche du dessin doit produire un tracé.

Règles :
- x va de gauche (0) à droite (1), y de haut (0) à bas (1), par rapport au terrain dessiné.
- "series" : pour tout alignement régulier (haie de plots, colonne de cerceaux, slalom de
  jalons…), donne le PREMIER et le DERNIER objet et leur nombre — n'énumère pas les objets un
  par un dans "elements", tu te tromperais de compte et d'espacement.
- "formes" : toute délimitation tracée au sol (carré de conservation, rectangle de zone,
  couloir, rond central matérialisé) devient une forme. x/y = coin haut-gauche, w/h = largeur
  et hauteur. Un couloir = un "rect" étroit. Ne mets PAS les lignes du terrain officiel.
- "rotation" : degrés dans le sens horaire, 0 = objet horizontal. Sers-t'en pour une échelle,
  une haie, une cage ou un couloir qui n'est pas horizontal sur le dessin.
- "traces" : flèche pleine = "deplacement" (course sans ballon) ou "conduite" (course avec
  ballon, souvent ondulée) ; flèche pointillée = "passe" ; flèche vers le but = "tir".
- Ne renvoie que les champs réellement DÉTECTABLES sur la photo — null ou listes vides sinon.
  N'invente rien : mieux vaut un schéma incomplet qu'un schéma inventé.
- "dominantes" et "sousPrincipes" : uniquement les codes listés ci-dessus, seulement si la
  fiche les évoque clairement.
- Si la photo est illisible ou sans rapport avec une séance de sport : {"lisible": false} et
  rien d'autre.
""";
}
