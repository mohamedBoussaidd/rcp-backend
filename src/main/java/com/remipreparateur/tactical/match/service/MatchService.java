package com.remipreparateur.tactical.match.service;

import com.remipreparateur.tactical.match.dto.MatchDtos.*;
import com.remipreparateur.tactical.match.entity.*;
import com.remipreparateur.tactical.match.repository.*;
import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.club.entity.Equipe;
import com.remipreparateur.club.repository.EquipeRepository;
import com.remipreparateur.club.repository.ClubRepository;
import com.remipreparateur.performance.seance.entity.Seance;
import com.remipreparateur.performance.seance.repository.SeanceRepository;
import com.remipreparateur.performance.gps.repository.DonneeGpsRepository;
import com.remipreparateur.auth.entity.Role;
import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.auth.rbac.PermissionResolver;
import com.remipreparateur.medical.blessure.repository.BlessureRepository;
import com.remipreparateur.notification.service.NotificationProducer;
import com.remipreparateur.saison.service.AppartenanceService;
import com.remipreparateur.shared.security.CurrentUserProvider;
import com.remipreparateur.shared.security.ScopeResolver;
import com.remipreparateur.tactical.regles.entity.RegleTactique;
import com.remipreparateur.tactical.regles.repository.RegleTactiqueRepository;
import com.remipreparateur.shared.time.Horloge;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Module Match (cycle de vie avant/après), niveau équipe.
 * Lecture : staff ; écriture : entraineur / president / super-admin (cf. SecurityConfig).
 * La session GPS liée et la charge par joueur s'appuient sur les `seance` / `donnee_gps`
 * déjà importées (lien manuel, aucune synchro automatique).
 */
@Service
public class MatchService {

    /** Les seuls types acceptés ; la base porte la même liste en contrainte CHECK (V99). */
    private static final Set<String> TYPES_MATCH = Set.of("AMICAL", "CHAMPIONNAT", "COUPE");

    /** Places sur le terrain. Le football à 11 est la seule pratique gérée à ce jour. */
    private static final int TITULAIRES_MAX = 11;

    private final MatchPrepaRepository matchRepository;
    private final MatchSchemaRepository schemaRepository;
    private final MatchCompoRepository compoRepository;
    private final MatchJoueurSurveilleRepository surveilleRepository;
    private final MatchSuspenduRepository suspenduRepository;
    private final JoueurRepository joueurRepository;
    private final EquipeRepository equipeRepository;
    private final ClubRepository clubRepository;
    private final SeanceRepository seanceRepository;
    private final DonneeGpsRepository donneeGpsRepository;
    private final BlessureRepository blessureRepository;
    private final NotificationProducer notificationProducer;
    private final CurrentUserProvider currentUser;
    private final ScopeResolver scopeResolver;
    private final PermissionResolver permissionResolver;
    private final Horloge horloge;
    private final AppartenanceService appartenance;
    private final RegleTactiqueRepository regleTactiqueRepository;

    public MatchService(MatchPrepaRepository matchRepository,
                        MatchSchemaRepository schemaRepository,
                        MatchCompoRepository compoRepository,
                        MatchJoueurSurveilleRepository surveilleRepository,
                        MatchSuspenduRepository suspenduRepository,
                        JoueurRepository joueurRepository,
                        EquipeRepository equipeRepository,
                        ClubRepository clubRepository,
                        SeanceRepository seanceRepository,
                        DonneeGpsRepository donneeGpsRepository,
                        BlessureRepository blessureRepository,
                        NotificationProducer notificationProducer,
                        CurrentUserProvider currentUser,
                        ScopeResolver scopeResolver,
                        PermissionResolver permissionResolver,
                        Horloge horloge,
                        AppartenanceService appartenance,
                        RegleTactiqueRepository regleTactiqueRepository) {
        this.matchRepository = matchRepository;
        this.schemaRepository = schemaRepository;
        this.compoRepository = compoRepository;
        this.surveilleRepository = surveilleRepository;
        this.suspenduRepository = suspenduRepository;
        this.joueurRepository = joueurRepository;
        this.equipeRepository = equipeRepository;
        this.clubRepository = clubRepository;
        this.seanceRepository = seanceRepository;
        this.donneeGpsRepository = donneeGpsRepository;
        this.blessureRepository = blessureRepository;
        this.notificationProducer = notificationProducer;
        this.currentUser = currentUser;
        this.scopeResolver = scopeResolver;
        this.permissionResolver = permissionResolver;
        this.horloge = horloge;
        this.appartenance = appartenance;
        this.regleTactiqueRepository = regleTactiqueRepository;
    }

    // ── Liste / création ──

    @Transactional(readOnly = true)
    public List<MatchResume> lister() {
        UUID equipeId = scopeResolver.equipeActiveUnique();
        // Hybride « voyage » : en date simulée (super-admin), on masque les matchs postérieurs à la
        // date simulée. Hors simulation, tout est listé (comportement inchangé).
        return matchRepository.findByEquipeIdOrderByDateMatchDescCreatedAtDesc(equipeId)
                .stream()
                .filter(m -> !horloge.estSimulee() || m.getDateMatch() == null
                        || !m.getDateMatch().isAfter(horloge.today()))
                .map(this::toResume).toList();
    }

    @Transactional
    public MatchResponse creer(MatchCreateRequest req) {
        Utilisateur u = currentUser.current();
        UUID equipeId = scopeResolver.equipeActiveUnique();
        MatchPrepa m = new MatchPrepa();
        m.setEquipeId(equipeId);
        m.setAdversaire(req.adversaire());
        m.setDateMatch(req.dateMatch());
        m.setCompetition(req.competition());
        m.setTypeMatch(typeMatchValide(req.typeMatch()));
        m.setDomicile(req.domicile());
        m.setCreePar(u.getId());
        return toResponse(matchRepository.save(m), u);
    }

    @Transactional(readOnly = true)
    public MatchResponse detail(UUID matchId) {
        MatchPrepa m = chargerMatch(matchId);
        return toResponse(m, currentUser.current());
    }

    @Transactional
    public MatchResponse modifierInfos(UUID matchId, MatchInfosRequest req) {
        MatchPrepa m = chargerMatch(matchId);
        m.setAdversaire(req.adversaire());
        m.setDateMatch(req.dateMatch());
        m.setHeureMatch(req.heureMatch());
        m.setCompetition(req.competition());
        m.setTypeMatch(typeMatchValide(req.typeMatch()));
        m.setDomicile(req.domicile());
        m.setConsignes(req.consignes());
        m.setLieuRdv(req.lieuRdv());
        m.setHeureRdv(req.heureRdv());
        m.setCouleurMaillot(req.couleurMaillot());
        m.setInfosLogistiques(req.infosLogistiques());
        return toResponse(touch(m), currentUser.current());
    }

    // ── Publication vers les joueurs ──

    @Transactional
    public MatchResponse publier(UUID matchId, PublicationRequest req) {
        MatchPrepa m = chargerMatch(matchId);
        boolean etaitPublie = m.isPublie();
        m.setCompoVisible(req.compoVisible());
        m.setPublie(req.publie());
        if (req.publie() && m.getPublieAt() == null) {
            m.setPublieAt(LocalDateTime.now());
        }
        MatchResponse resp = toResponse(touch(m), currentUser.current());
        if (req.publie() && !etaitPublie) {
            notificationProducer.matchPublie(m.getEquipeId(), m.getAdversaire());
        }
        return resp;
    }

    // ── Suspensions (indisponibilité manuelle pour ce match) ──

    @Transactional
    public MatchResponse definirSuspendus(UUID matchId, SuspendusRequest req) {
        MatchPrepa m = chargerMatch(matchId);
        suspenduRepository.deleteByMatchId(m.getId());
        suspenduRepository.flush();
        if (req.joueurIds() != null) {
            for (UUID joueurId : req.joueurIds().stream().distinct().toList()) {
                MatchSuspendu s = new MatchSuspendu();
                s.setMatchId(m.getId());
                s.setJoueurId(joueurId);
                suspenduRepository.save(s);
            }
        }
        return toResponse(touch(m), currentUser.current());
    }

    /** Compo du match précédent de l'équipe (le plus récent autre que celui-ci), pour la reprendre. */
    @Transactional(readOnly = true)
    public List<CompoItemResponse> compoDernierMatch(UUID matchId) {
        MatchPrepa courant = chargerMatch(matchId);
        var precedent = matchRepository.findByEquipeIdOrderByDateMatchDescCreatedAtDesc(courant.getEquipeId())
                .stream().filter(x -> !x.getId().equals(matchId)).findFirst().orElse(null);
        if (precedent == null) return List.of();
        List<MatchCompo> compos = compoRepository.findByMatchId(precedent.getId());
        if (compos.isEmpty()) return List.of();
        Map<UUID, Joueur> joueurs = joueurRepository.findAllById(compos.stream().map(MatchCompo::getJoueurId).toList())
                .stream().collect(Collectors.toMap(Joueur::getId, Function.identity()));
        return compos.stream().map(c -> toCompoItem(c, joueurs.get(c.getJoueurId()), true)).toList();
    }

    @Transactional
    public MatchResponse modifierDebrief(UUID matchId, MatchDebriefRequest req) {
        MatchPrepa m = chargerMatch(matchId);
        m.setResultat(req.resultat());
        m.setButsPour(req.butsPour());
        m.setButsContre(req.butsContre());
        m.setScore(scoreAffiche(req.butsPour(), req.butsContre()));
        m.setNotesDebrief(req.notesDebrief());
        return toResponse(touch(m), currentUser.current());
    }

    /**
     * Le score reste une chaîne partout ailleurs (cartes de match, fil de vie) : on la reconstruit
     * ici plutôt que de la faire saisir. Un seul des deux nombres renseigné ne produit rien —
     * afficher « 2- » serait pire que rien.
     */
    private String scoreAffiche(Short pour, Short contre) {
        if (pour == null || contre == null) return null;
        return pour + "-" + contre;
    }

    /** Un type inconnu retomberait sur la contrainte CHECK de la base : on le ramène au défaut. */
    private String typeMatchValide(String type) {
        if (type == null) return "CHAMPIONNAT";
        String t = type.trim().toUpperCase();
        return TYPES_MATCH.contains(t) ? t : "CHAMPIONNAT";
    }

    @Transactional
    public void supprimer(UUID matchId) {
        MatchPrepa m = chargerMatch(matchId);
        matchRepository.delete(m);
    }

    // ── Schémas adverses ──

    @Transactional
    public SchemaResponse ajouterSchema(UUID matchId, SchemaRequest req) {
        MatchPrepa m = chargerMatch(matchId);
        List<MatchSchema> existants = schemaRepository.findByMatchIdOrderByOrdreAsc(m.getId());
        int ordre = existants.isEmpty() ? 0 : existants.get(existants.size() - 1).getOrdre() + 1;
        MatchSchema s = new MatchSchema();
        s.setMatchId(m.getId());
        s.setTitre(req.titre());
        s.setSchemaJson(req.schemaJson());
        s.setApercu(req.apercu());
        s.setOrdre(ordre);
        return toSchemaResponse(schemaRepository.save(s));
    }

    @Transactional
    public SchemaResponse modifierSchema(UUID schemaId, SchemaRequest req) {
        MatchSchema s = chargerSchema(schemaId);
        s.setTitre(req.titre());
        s.setSchemaJson(req.schemaJson());
        s.setApercu(req.apercu());
        return toSchemaResponse(schemaRepository.save(s));
    }

    @Transactional
    public void supprimerSchema(UUID schemaId) {
        MatchSchema s = chargerSchema(schemaId);
        schemaRepository.delete(s);
    }

    // ── Compo ──

    @Transactional
    public MatchResponse enregistrerCompo(UUID matchId, CompoUpdateRequest req) {
        MatchPrepa m = chargerMatch(matchId);
        verifierOnzeTitulaires(req);
        // La compo est réécrite en entier à chaque enregistrement (delete + recréation). Depuis
        // V97 ces lignes portent aussi la feuille de match : sans ce report, corriger un placement
        // après la rencontre effacerait silencieusement minutes, buteurs et cartons déjà saisis.
        Map<UUID, MatchCompo> feuilleExistante = compoRepository.findByMatchId(m.getId()).stream()
                .collect(Collectors.toMap(MatchCompo::getJoueurId, Function.identity(), (a, b) -> a));

        compoRepository.deleteByMatchId(m.getId());
        compoRepository.flush();
        for (CompoItemRequest item : req.placements()) {
            MatchCompo c = new MatchCompo();
            c.setMatchId(m.getId());
            c.setJoueurId(item.joueurId());
            c.setX(item.x() != null ? item.x() : BigDecimal.ZERO);
            c.setY(item.y() != null ? item.y() : BigDecimal.ZERO);
            c.setStatut(item.statut() != null ? item.statut() : "TITULAIRE");
            c.setConsigne(item.consigne());
            reporterFeuille(feuilleExistante.get(item.joueurId()), c);
            compoRepository.save(c);
        }
        return toResponse(touch(m), currentUser.current());
    }

    /**
     * Onze titulaires au maximum. Le terrain n'en accepte pas davantage, et un douzième maillot
     * posé par mégarde ne se voyait jusqu'ici qu'au compteur de l'en-tête — puis faussait le
     * décompte de titularisations de tous les joueurs concernés.
     */
    private void verifierOnzeTitulaires(CompoUpdateRequest req) {
        long titulaires = req.placements().stream()
                .filter(p -> "TITULAIRE".equals(p.statut()))
                .count();
        if (titulaires > TITULAIRES_MAX) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    titulaires + " titulaires pour " + TITULAIRES_MAX + " places sur le terrain.");
        }
    }

    /** Recopie la feuille de match d'une ligne de compo vers celle qui la remplace. */
    private void reporterFeuille(MatchCompo ancienne, MatchCompo nouvelle) {
        if (ancienne == null) {
            // Pas d'antécédent : un titulaire est réputé entré en jeu, un remplaçant non.
            nouvelle.setEntreEnJeu("TITULAIRE".equals(nouvelle.getStatut()));
            return;
        }
        nouvelle.setEntreEnJeu(ancienne.isEntreEnJeu() || "TITULAIRE".equals(nouvelle.getStatut()));
        nouvelle.setMinuteEntree(ancienne.getMinuteEntree());
        nouvelle.setMinuteSortie(ancienne.getMinuteSortie());
        nouvelle.setTempsJeuFederation(ancienne.getTempsJeuFederation());
        nouvelle.setButs(ancienne.getButs());
        nouvelle.setPassesDecisives(ancienne.getPassesDecisives());
        nouvelle.setCartonsJaunes(ancienne.getCartonsJaunes());
        nouvelle.setCartonRouge(ancienne.isCartonRouge());
    }

    // ── Feuille de match (V97) ──────────────────────────────────────────────

    /**
     * Durée de référence quand un joueur est entré sans qu'on ait noté sa sortie : il a fini la
     * rencontre. C'est une APPROXIMATION — temps additionnel non compté, et les catégories jeunes
     * jouent moins de 90 minutes. Toute valeur saisie explicitement prime sur ce calcul.
     */
    private static final int DUREE_MATCH_REFERENCE_MIN = 90;

    @Transactional(readOnly = true)
    public FeuilleResponse feuille(UUID matchId) {
        MatchPrepa m = chargerMatch(matchId);
        Utilisateur u = currentUser.current();

        Map<UUID, Short> gpsParJoueur = tempsGpsParJoueur(m);
        List<MatchCompo> compo = compoRepository.findByMatchId(m.getId());
        Map<UUID, Joueur> joueurs = joueurRepository.findAllById(
                        compo.stream().map(MatchCompo::getJoueurId).toList()).stream()
                .collect(Collectors.toMap(Joueur::getId, Function.identity()));

        List<FeuilleLigneResponse> lignes = compo.stream()
                .map(c -> toFeuilleLigne(c, joueurs.get(c.getJoueurId()), gpsParJoueur.get(c.getJoueurId()),
                        m.getButsContre()))
                .sorted((a, b) -> {
                    // Les joueurs qui ont du temps de jeu d'abord : c'est sur eux qu'on saisit.
                    int parJeu = Boolean.compare(b.entreEnJeu(), a.entreEnJeu());
                    return parJeu != 0 ? parJeu : nomComplet(a).compareToIgnoreCase(nomComplet(b));
                })
                .toList();

        return new FeuilleResponse(m.getId(), peutModifier(u), m.getSessionGpsId() != null,
                m.getButsPour(), m.getButsContre(), lignes);
    }

    /**
     * Clean sheet d'un joueur sur une rencontre : l'équipe n'a rien encaissé ET il était sur le
     * terrain. Déduit, jamais saisi — {@code null} tant que les buts encaissés ne sont pas
     * renseignés, ce qui vaut mieux qu'un « aucun clean sheet » qui passerait pour un fait.
     */
    static Boolean cleanSheet(boolean entreEnJeu, Short butsContre) {
        if (butsContre == null) return null;
        return butsContre == 0 && entreEnJeu;
    }

    @Transactional
    public FeuilleResponse enregistrerFeuille(UUID matchId, FeuilleUpdateRequest req) {
        MatchPrepa m = chargerMatch(matchId);
        verifierButsCoherents(m, req);
        Map<UUID, MatchCompo> parJoueur = compoRepository.findByMatchId(m.getId()).stream()
                .collect(Collectors.toMap(MatchCompo::getJoueurId, Function.identity(), (a, b) -> a));

        for (FeuilleLigneRequest l : req.lignes()) {
            MatchCompo c = parJoueur.get(l.joueurId());
            // Une ligne pour un joueur absent de la compo est ignorée : la feuille se remplit sur
            // le groupe convoqué, elle n'est pas un moyen détourné d'ajouter quelqu'un au match.
            if (c == null) continue;
            c.setEntreEnJeu(l.entreEnJeu());
            c.setMinuteEntree(l.minuteEntree());
            c.setMinuteSortie(l.minuteSortie());
            c.setButs(nonNegatif(l.buts()));
            c.setPassesDecisives(nonNegatif(l.passesDecisives()));
            short jaunes = nonNegatif(l.cartonsJaunes());
            c.setCartonsJaunes(jaunes);
            // Deux avertissements valent expulsion : la règle est appliquée ici et pas seulement à
            // l'écran, sinon un appel direct à l'API produirait un joueur à 2 jaunes sans rouge —
            // et le cumul d'avertissements, plus bas, compterait un match de trop.
            c.setCartonRouge(l.cartonRouge() || jaunes >= 2);
            compoRepository.save(c);
        }
        touch(m);
        return feuille(matchId);
    }

    /**
     * Les buteurs ne peuvent pas dépasser le score de l'équipe : quatre buts attribués sur un 2-0
     * n'était refusé nulle part, et faussait durablement l'onglet Compétition du joueur. La somme
     * peut en revanche rester INFÉRIEURE au score — un but contre son camp adverse n'a pas de
     * buteur de notre côté. Sans buts marqués renseignés, rien à vérifier.
     */
    private void verifierButsCoherents(MatchPrepa m, FeuilleUpdateRequest req) {
        if (m.getButsPour() == null) return;
        int total = req.lignes().stream().mapToInt(l -> nonNegatif(l.buts())).sum();
        if (total > m.getButsPour()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Buteurs incohérents : " + total + " but(s) attribué(s) pour un score de "
                    + m.getButsPour() + ". Corrigez les buteurs ou le score du débrief.");
        }
    }

    private static short nonNegatif(Short v) {
        return v == null || v < 0 ? 0 : v;
    }

    private static String nomComplet(FeuilleLigneResponse l) {
        return ((l.nom() != null ? l.nom() : "") + " " + (l.prenom() != null ? l.prenom() : "")).trim();
    }

    /**
     * Temps de port du capteur par joueur sur la séance liée au match. Ce n'est PAS un temps de
     * jeu : il n'est retenu plus bas que pour les joueurs déclarés entrés en jeu.
     */
    private Map<UUID, Short> tempsGpsParJoueur(MatchPrepa m) {
        if (m.getSessionGpsId() == null) return Map.of();
        return donneeGpsRepository.findBySeanceId(m.getSessionGpsId()).stream()
                .filter(g -> g.getJoueur() != null && g.getDureeMinutes() != null)
                .collect(Collectors.toMap(g -> g.getJoueur().getId(), g -> g.getDureeMinutes(), (a, b) -> a));
    }

    private FeuilleLigneResponse toFeuilleLigne(MatchCompo c, Joueur j, Short gpsMinutes, Short butsContre) {
        Integer saisi = tempsJeuSaisi(c);
        Integer federation = c.getTempsJeuFederation() != null ? c.getTempsJeuFederation().intValue() : null;
        // Le capteur ne vaut temps de jeu que si le joueur est effectivement entré : un remplaçant
        // qui s'échauffe 40 minutes avec son capteur en ressortirait sinon avec 40 minutes jouées.
        Integer gps = (c.isEntreEnJeu() && gpsMinutes != null) ? gpsMinutes.intValue() : null;

        Integer retenu;
        String source;
        if (saisi != null)            { retenu = saisi;      source = "SAISIE"; }
        else if (federation != null)  { retenu = federation; source = "FEDERATION"; }
        else if (gps != null)         { retenu = gps;        source = "GPS"; }
        else                          { retenu = null;       source = null; }

        return new FeuilleLigneResponse(
                c.getJoueurId(),
                j != null ? j.getNom() : null,
                j != null ? j.getPrenom() : null,
                j != null ? j.getPostePrincipal() : null,
                c.getStatut(), c.isEntreEnJeu(),
                c.getMinuteEntree(), c.getMinuteSortie(),
                saisi, federation, gps, retenu, source,
                c.getButs(), c.getPassesDecisives(), c.getCartonsJaunes(),
                c.isCartonRouge(), cleanSheet(c.isEntreEnJeu(), butsContre));
    }

    /**
     * Minutes déduites de la saisie. Entrée sans sortie = a fini le match ; sortie sans entrée =
     * était sur le terrain au coup d'envoi. Aucune minute renseignée = rien à déduire (et non zéro),
     * les autres sources prennent alors le relais.
     */
    private Integer tempsJeuSaisi(MatchCompo c) {
        // Déclaré non entré = zéro minute, et c'est une certitude : aucune autre source ne doit
        // venir lui prêter du temps de jeu.
        if (!c.isEntreEnJeu()) return 0;
        Integer entree = c.getMinuteEntree() != null ? c.getMinuteEntree().intValue() : null;
        Integer sortie = c.getMinuteSortie() != null ? c.getMinuteSortie().intValue() : null;
        if (entree == null && sortie == null) return null;
        int debut = entree != null ? entree : 0;
        int fin = sortie != null ? sortie : DUREE_MATCH_REFERENCE_MIN;
        return Math.max(fin - debut, 0);
    }

    // ── Joueurs à surveiller ──

    @Transactional
    public MatchResponse ajouterSurveille(UUID matchId, SurveilleRequest req) {
        MatchPrepa m = chargerMatch(matchId);
        MatchJoueurSurveille s = new MatchJoueurSurveille();
        s.setMatchId(m.getId());
        s.setCible("EQUIPE".equals(req.cible()) ? "EQUIPE" : "ADVERSE");
        s.setJoueurId("EQUIPE".equals(s.getCible()) ? req.joueurId() : null);
        s.setNom(req.nom());
        s.setNote(req.note());
        surveilleRepository.save(s);
        return toResponse(touch(m), currentUser.current());
    }

    @Transactional
    public MatchResponse supprimerSurveille(UUID surveilleId) {
        MatchJoueurSurveille s = surveilleRepository.findById(surveilleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entrée introuvable"));
        MatchPrepa m = chargerMatch(s.getMatchId()); // vérifie le périmètre via le match parent
        surveilleRepository.delete(s);
        return toResponse(touch(m), currentUser.current());
    }

    // ── Session GPS ──

    @Transactional
    public MatchResponse definirSessionGps(UUID matchId, SessionGpsRequest req) {
        MatchPrepa m = chargerMatch(matchId);
        if (req.sessionGpsId() != null) {
            Seance s = seanceRepository.findById(req.sessionGpsId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session introuvable"));
            scopeResolver.verifieAcces(s.getEquipeId());
        }
        m.setSessionGpsId(req.sessionGpsId());
        return toResponse(touch(m), currentUser.current());
    }

    // ── Profil de règles adverses (moteur tactique) ──

    /** Attache (ou détache : null) un profil de règles ADVERSAIRE au match — référence, pas copie. */
    @Transactional
    public MatchResponse definirProfilAdverse(UUID matchId, ProfilAdverseRequest req) {
        MatchPrepa m = chargerMatch(matchId);
        if (req.profilAdverseId() != null) {
            RegleTactique r = regleTactiqueRepository.findById(req.profilAdverseId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profil introuvable"));
            scopeResolver.verifieAcces(r.getEquipeId());
            if (!"ADVERSAIRE".equals(r.getType())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Seul un profil ADVERSAIRE peut être attaché à un match");
            }
        }
        m.setProfilAdverseId(req.profilAdverseId());
        return toResponse(touch(m), currentUser.current());
    }

    /** Séances de l'équipe active, proposées comme sessions GPS à lier. */
    @Transactional(readOnly = true)
    public List<SessionGpsOption> sessionsDisponibles() {
        UUID equipeId = scopeResolver.equipeActiveUnique();
        return seanceRepository.findByEquipeIdIn(List.of(equipeId)).stream()
                .sorted((a, b) -> b.getDate().compareTo(a.getDate()))
                .map(s -> new SessionGpsOption(s.getId(), s.getDate(), libelleSeance(s)))
                .toList();
    }

    /** Charge GPS par joueur issue de la session liée (vide si aucune session liée). */
    @Transactional(readOnly = true)
    public List<ChargeJoueur> chargeGps(UUID matchId) {
        MatchPrepa m = chargerMatch(matchId);
        if (m.getSessionGpsId() == null) {
            return List.of();
        }
        return donneeGpsRepository.findBySeanceId(m.getSessionGpsId()).stream()
                .map(g -> {
                    Joueur j = g.getJoueur();
                    return new ChargeJoueur(j.getId(), j.getNom(), j.getPrenom(),
                            g.getDureeMinutes(), g.getDistanceTotaleM(),
                            g.getDistanceSprint24kmhM(), g.getNbSprints24kmh(), g.getVitesseMaxKmh());
                })
                .sorted((a, b) -> a.nom().compareToIgnoreCase(b.nom()))
                .toList();
    }

    /** Récap des apparitions par statut, par joueur, sur tous les matchs de l'équipe active. */
    @Transactional(readOnly = true)
    public List<JoueurCompoStats> statsCompo() {
        UUID equipeId = scopeResolver.equipeActiveUnique();
        var aggs = compoRepository.aggregerStatuts(equipeId);
        if (aggs.isEmpty()) return List.of();

        // Compteurs par joueur : index 0=TIT,1=REM,2=RES,3=REP,4=SUS
        Map<UUID, long[]> parJoueur = new java.util.LinkedHashMap<>();
        for (var a : aggs) {
            long[] c = parJoueur.computeIfAbsent(a.getJoueurId(), k -> new long[5]);
            switch (a.getStatut()) {
                case "TITULAIRE" -> c[0] += a.getNb();
                case "REMPLACANT" -> c[1] += a.getNb();
                case "RESERVE" -> c[2] += a.getNb();
                case "REPOS" -> c[3] += a.getNb();
                case "SUSPENDU" -> c[4] += a.getNb();
                default -> { }
            }
        }
        Map<UUID, Joueur> joueurs = joueurRepository.findAllById(parJoueur.keySet())
                .stream().collect(Collectors.toMap(Joueur::getId, Function.identity()));
        return parJoueur.entrySet().stream().map(e -> {
            long[] c = e.getValue();
            Joueur j = joueurs.get(e.getKey());
            long total = c[0] + c[1] + c[2] + c[3] + c[4];
            return new JoueurCompoStats(e.getKey(),
                    j != null ? j.getNom() : null,
                    j != null ? j.getPrenom() : null,
                    j != null ? j.getPostePrincipal() : null,
                    c[0], c[1], c[2], c[3], c[4], total);
        })
        .sorted((a, b) -> Long.compare(b.total(), a.total()))
        .toList();
    }

    /** Identifiants des joueurs de l'équipe active actuellement blessés (sans retour effectif). */
    @Transactional(readOnly = true)
    public List<UUID> joueursBlesses() {
        UUID equipeId = scopeResolver.equipeActiveUnique();
        return blessureRepository.findByEquipeIdAndDateRetourEffectifIsNull(equipeId)
                .stream().map(b -> b.getJoueurId()).distinct().toList();
    }

    // ════════════ Lecture côté JOUEUR (matchs publiés de son équipe) ════════════

    @Transactional(readOnly = true)
    public List<MatchJoueurResume> listerPourJoueur(UUID joueurId) {
        UUID equipeId = equipeDuJoueur(joueurId);
        return matchRepository.findByEquipeIdAndPublieTrueOrderByDateMatchDescCreatedAtDesc(equipeId)
                .stream().map(m -> {
                    MatchCompo mien = compoRepository.findByMatchId(m.getId()).stream()
                            .filter(c -> c.getJoueurId().equals(joueurId)).findFirst().orElse(null);
                    return new MatchJoueurResume(m.getId(), m.getAdversaire(), m.getDateMatch(), m.getHeureMatch(),
                            m.getCompetition(), m.isDomicile(), mien != null ? mien.getStatut() : null);
                }).toList();
    }

    @Transactional(readOnly = true)
    public MatchJoueurDetail detailPourJoueur(UUID joueurId, UUID matchId) {
        UUID equipeId = equipeDuJoueur(joueurId);
        MatchPrepa m = matchRepository.findById(matchId)
                .filter(x -> x.getEquipeId().equals(equipeId) && x.isPublie())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match introuvable"));

        List<MatchCompo> compos = compoRepository.findByMatchId(m.getId());
        Map<UUID, Joueur> joueurs = compos.isEmpty() ? Map.of()
                : joueurRepository.findAllById(compos.stream().map(MatchCompo::getJoueurId).toList())
                    .stream().collect(Collectors.toMap(Joueur::getId, Function.identity()));

        MatchCompo mien = compos.stream().filter(c -> c.getJoueurId().equals(joueurId)).findFirst().orElse(null);
        String monStatut = mien != null ? mien.getStatut() : null;
        String maConsigne = mien != null ? mien.getConsigne() : null;

        boolean visible = m.isCompoVisible();
        List<CompoItemResponse> compo = compos.stream()
                .map(c -> toCompoItem(c, joueurs.get(c.getJoueurId()), visible)).toList();

        // Non convoqués (joueurs de l'équipe absents de la compo) : montrés seulement si compo visible.
        List<NomJoueur> nonConvoques = List.of();
        if (visible) {
            java.util.Set<UUID> dansCompo = compos.stream().map(MatchCompo::getJoueurId).collect(Collectors.toSet());
            nonConvoques = joueurRepository.findByEquipeIdIn(List.of(equipeId)).stream()
                    .filter(j -> !dansCompo.contains(j.getId()))
                    .map(j -> new NomJoueur(j.getId(), j.getNom(), j.getPrenom(), j.getPostePrincipal()))
                    .toList();
        }

        List<SchemaResponse> schemas = schemaRepository.findByMatchIdOrderByOrdreAsc(m.getId())
                .stream().map(this::toSchemaResponse).toList();
        List<SurveilleResponse> surveilles = surveilleRepository.findByMatchIdOrderByCreatedAtAsc(m.getId())
                .stream().map(this::toSurveilleResponse).toList();

        return new MatchJoueurDetail(m.getId(), m.getAdversaire(), nomClubDeLEquipe(equipeId),
                m.getDateMatch(), m.getHeureMatch(), m.getCompetition(),
                m.isDomicile(), m.getLieuRdv(), m.getHeureRdv(), m.getCouleurMaillot(), m.getInfosLogistiques(),
                m.getConsignes(), monStatut, maConsigne, visible, compo, nonConvoques, schemas, surveilles);
    }

    /** Nom du club de l'équipe (pour le résumé « VS » côté joueur), avec repli sur le nom d'équipe. */
    private String nomClubDeLEquipe(UUID equipeId) {
        Equipe e = equipeRepository.findById(equipeId).orElse(null);
        if (e == null) return null;
        return clubRepository.findById(e.getClubId()).map(c -> c.getNom()).orElse(e.getNom());
    }

    private UUID equipeDuJoueur(UUID joueurId) {
        UUID eq = appartenance.equipePrincipale(joueurId);   // équipe dérivée de l'effectif (Phase 4)
        if (eq == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Joueur sans équipe");
        }
        return eq;
    }

    // ── Helpers de chargement / périmètre ──

    private MatchPrepa chargerMatch(UUID matchId) {
        MatchPrepa m = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match introuvable"));
        scopeResolver.verifieAcces(m.getEquipeId());
        return m;
    }

    private MatchSchema chargerSchema(UUID schemaId) {
        MatchSchema s = schemaRepository.findById(schemaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schéma introuvable"));
        chargerMatch(s.getMatchId()); // vérifie le périmètre via le match parent
        return s;
    }

    private MatchPrepa touch(MatchPrepa m) {
        m.setUpdatedAt(LocalDateTime.now());
        return matchRepository.save(m);
    }

    private String libelleSeance(Seance s) {
        String base = s.getTitre() != null && !s.getTitre().isBlank()
                ? s.getTitre()
                : (s.getTypeSeance() != null ? s.getTypeSeance().getLibelle() : "Séance");
        return s.getAdversaire() != null && !s.getAdversaire().isBlank()
                ? base + " — " + s.getAdversaire()
                : base;
    }

    // ── Mapping ──

    private MatchResume toResume(MatchPrepa m) {
        return new MatchResume(m.getId(), m.getAdversaire(), m.getDateMatch(), m.getCompetition(),
                m.getTypeMatch(), m.isDomicile(), m.getResultat(), m.getScore(),
                m.getSessionGpsId() != null, m.isPublie());
    }

    private MatchResponse toResponse(MatchPrepa m, Utilisateur u) {
        List<SchemaResponse> schemas = schemaRepository.findByMatchIdOrderByOrdreAsc(m.getId())
                .stream().map(this::toSchemaResponse).toList();
        List<MatchCompo> compos = compoRepository.findByMatchId(m.getId());
        Map<UUID, Joueur> joueurs = compos.isEmpty() ? Map.of()
                : joueurRepository.findAllById(compos.stream().map(MatchCompo::getJoueurId).toList())
                    .stream().collect(Collectors.toMap(Joueur::getId, Function.identity()));
        List<CompoItemResponse> compo = compos.stream()
                .map(c -> toCompoItem(c, joueurs.get(c.getJoueurId()), true)).toList();
        List<SurveilleResponse> surveilles = surveilleRepository.findByMatchIdOrderByCreatedAtAsc(m.getId())
                .stream().map(this::toSurveilleResponse).toList();
        List<UUID> suspendus = suspenduRepository.findByMatchId(m.getId())
                .stream().map(MatchSuspendu::getJoueurId).toList();
        return new MatchResponse(m.getId(), m.getEquipeId(), peutModifier(u),
                m.getAdversaire(), m.getDateMatch(), m.getCompetition(), m.getTypeMatch(), m.isDomicile(),
                m.getConsignes(), m.getLieuRdv(), m.getHeureRdv(), m.getHeureMatch(), m.getCouleurMaillot(), m.getInfosLogistiques(),
                m.isPublie(), m.getPublieAt(), m.isCompoVisible(),
                m.getResultat(), m.getScore(), m.getButsPour(), m.getButsContre(), m.getNotesDebrief(),
                m.getSessionGpsId(), m.getProfilAdverseId(), schemas, compo, surveilles, suspendus, m.getUpdatedAt());
    }

    /** Mappe un placement de compo. Si {@code positions} est faux, masque x/y (compo non visible au joueur). */
    private CompoItemResponse toCompoItem(MatchCompo c, Joueur j, boolean positions) {
        return new CompoItemResponse(c.getJoueurId(),
                j != null ? j.getNom() : null,
                j != null ? j.getPrenom() : null,
                j != null ? j.getPostePrincipal() : null,
                positions ? c.getX() : BigDecimal.ZERO,
                positions ? c.getY() : BigDecimal.ZERO,
                c.getStatut(), c.getConsigne());
    }

    private SchemaResponse toSchemaResponse(MatchSchema s) {
        return new SchemaResponse(s.getId(), s.getTitre(), s.getSchemaJson(), s.getApercu(), s.getOrdre());
    }

    private SurveilleResponse toSurveilleResponse(MatchJoueurSurveille s) {
        String nom = s.getNom();
        if ("EQUIPE".equals(s.getCible()) && s.getJoueurId() != null) {
            // Nom d'affichage = nom du joueur (snapshot non stocké : on le résout à la lecture).
            nom = joueurRepository.findById(s.getJoueurId())
                    .map(j -> (j.getPrenom() + " " + j.getNom()).trim())
                    .orElse(nom);
        }
        return new SurveilleResponse(s.getId(), s.getCible(), s.getJoueurId(), nom, s.getNote());
    }

    private boolean peutModifier(Utilisateur u) {
        return u.getRole() == Role.SUPER_ADMIN || permissionResolver.permissionsPour(u).contains("matchs:write");
    }
}
