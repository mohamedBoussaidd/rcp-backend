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
            default -> "";
        };
    }

    /**
     * Prompt vision par défaut de l'import photo. Énumère explicitement la palette du
     * schéma, les référentiels du mode avancé (codes V61) et le contrat JSON strict.
     * Éditable par le super-admin (écran Paramètres IA) — ce texte n'est que le fallback.
     */
    public static final String PROMPT_IMPORT_PHOTO_DEFAUT = """
Tu analyses la photo d'une fiche de séance ou d'exercice de football dessinée sur papier par un entraîneur (texte manuscrit ou imprimé + éventuel schéma tactique).

Réponds UNIQUEMENT avec un objet JSON valide, sans aucun texte autour, sans balises markdown. Contrat exact :

{
  "lisible": true,
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
    "terrain": "complet" ou "demi" (demi si le dessin montre un demi-terrain ou une zone réduite),
    "elements": [ { "type": "joueur"|"plot"|"ballon"|"but"|"cerceau"|"mannequin", "couleur": "nous"|"adverse"|"neutre", "numero": nombre ou null, "x": 0.0-1.0, "y": 0.0-1.0 } ],
    "traces": [ { "type": "passe"|"deplacement"|"conduite"|"tir", "points": [x1, y1, x2, y2, ...] avec chaque coordonnée entre 0.0 et 1.0 } ]
  }
}

Règles :
- x va de gauche (0) à droite (1), y de haut (0) à bas (1), par rapport au terrain dessiné.
- Deux équipes distinctes (croix/ronds, deux couleurs) → l'une "nous", l'autre "adverse" ; objets neutres (plots, ballons, buts, cerceaux, mannequins) → "neutre".
- Ne renvoie que les champs réellement DÉTECTABLES sur la photo — null ou listes vides sinon. N'invente rien.
- "dominantes" et "sousPrincipes" : uniquement les codes listés ci-dessus, seulement si la fiche les évoque clairement.
- Si la photo est illisible ou sans rapport avec une séance de sport : {"lisible": false} et rien d'autre.
""";
}
