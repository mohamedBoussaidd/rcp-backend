package com.remipreparateur.tactical.match.service;

import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.medical.blessure.entity.Blessure;
import com.remipreparateur.medical.blessure.repository.BlessureRepository;
import com.remipreparateur.performance.gps.repository.DonneeGpsRepository;
import com.remipreparateur.performance.rpe.entity.RpeSeance;
import com.remipreparateur.performance.rpe.repository.RpeSeanceRepository;
import com.remipreparateur.shared.security.ScopeResolver;
import com.remipreparateur.shared.time.Horloge;
import com.remipreparateur.tactical.match.dto.StatsCompetitionDtos.*;
import com.remipreparateur.tactical.match.entity.MatchCompo;
import com.remipreparateur.tactical.match.entity.MatchPrepa;
import com.remipreparateur.tactical.match.repository.MatchCompoRepository;
import com.remipreparateur.tactical.match.repository.MatchPrepaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Statistiques de compétition d'un joueur (module add-on {@code stats_competition}).
 *
 * <p>Alimente l'onglet « Compétition » de la fiche joueur : temps de jeu et sa provenance,
 * participation au groupe, polyvalence réelle, production offensive et discipline, puis le
 * croisement avec la charge d'entraînement.
 *
 * <p>Aucune donnée n'est recalculée ici : tout est lu sur la feuille de match (V97), la compo
 * et les sources de charge existantes. La règle de priorité du temps de jeu — saisie, puis
 * fédération, puis GPS — est appliquée à un seul endroit, {@link #tempsJeuRetenu}.
 */
@Service
public class StatsCompetitionService {

    /** Voir {@code MatchService} : durée de référence quand la sortie n'est pas notée. */
    private static final int DUREE_MATCH_REFERENCE_MIN = 90;

    private final MatchPrepaRepository matchRepository;
    private final MatchCompoRepository compoRepository;
    private final JoueurRepository joueurRepository;
    private final DonneeGpsRepository donneeGpsRepository;
    private final RpeSeanceRepository rpeRepository;
    private final BlessureRepository blessureRepository;
    private final ScopeResolver scopeResolver;
    private final Horloge horloge;

    public StatsCompetitionService(MatchPrepaRepository matchRepository,
                                   MatchCompoRepository compoRepository,
                                   JoueurRepository joueurRepository,
                                   DonneeGpsRepository donneeGpsRepository,
                                   RpeSeanceRepository rpeRepository,
                                   BlessureRepository blessureRepository,
                                   ScopeResolver scopeResolver,
                                   Horloge horloge) {
        this.matchRepository = matchRepository;
        this.compoRepository = compoRepository;
        this.joueurRepository = joueurRepository;
        this.donneeGpsRepository = donneeGpsRepository;
        this.rpeRepository = rpeRepository;
        this.blessureRepository = blessureRepository;
        this.scopeResolver = scopeResolver;
        this.horloge = horloge;
    }

    @Transactional(readOnly = true)
    public StatsJoueur statsJoueur(UUID joueurId, LocalDate depuis) {
        Joueur j = joueurRepository.findById(joueurId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Joueur introuvable"));
        scopeResolver.verifieAccesPersonne(j.getId(), j.getClubId());

        // Les matchs sont une ressource « une par équipe » : on lit ceux de l'équipe active, comme
        // le fait l'écran Match. L'appartenance du joueur ne vit plus sur la fiche depuis V51.
        UUID equipeId;
        try {
            equipeId = scopeResolver.equipeActiveUnique();
        } catch (ResponseStatusException e) {
            // Aucune équipe active (président multi-équipes sans contexte) : vue vide plutôt qu'un
            // 409 qui casserait l'ouverture de la fiche entière.
            return vide(j, depuis);
        }

        LocalDate fin = horloge.today();
        LocalDate debut = depuis != null ? depuis : fin.minusYears(1);

        // Matchs de l'équipe sur la période : dénominateur de la participation et du temps possible.
        List<MatchPrepa> matchsEquipe = matchRepository.findByEquipeIdOrderByDateMatchDescCreatedAtDesc(equipeId).stream()
                .filter(m -> m.getDateMatch() != null
                        && !m.getDateMatch().isBefore(debut) && !m.getDateMatch().isAfter(fin))
                .toList();

        List<MatchCompo> compos = compoRepository.historiqueJoueur(joueurId, equipeId, debut).stream()
                .map(MatchCompoRepository.CompoMatchAgg::getCompo)
                .toList();
        Map<UUID, MatchCompo> parMatch = compos.stream()
                .collect(Collectors.toMap(MatchCompo::getMatchId, c -> c, (a, b) -> a));

        List<MatchJoueur> lignes = new ArrayList<>();
        for (MatchPrepa m : matchsEquipe) {
            MatchCompo c = parMatch.get(m.getId());
            if (c == null) continue;   // non convoqué : compté plus bas, pas d'entrée détaillée
            Integer gps = tempsGps(m, joueurId, c);
            Integer retenu = tempsJeuRetenu(c, gps);
            lignes.add(new MatchJoueur(
                    m.getId(), m.getDateMatch(), m.getAdversaire(), m.getCompetition(), m.isDomicile(),
                    m.getResultat(), m.getScore(), c.getStatut(), c.isEntreEnJeu(),
                    retenu, sourceTempsJeu(c, gps),
                    c.getButs(), c.getPassesDecisives(), c.getCartonsJaunes(),
                    c.isCartonRouge(),
                    // Déduit du score du match, comme sur la feuille : un clean sheet figé en base
                    // deviendrait faux au premier score corrigé après coup.
                    Boolean.TRUE.equals(MatchService.cleanSheet(c.isEntreEnJeu(), m.getButsContre())),
                    posteDepuisPlacement(c)));
        }

        return new StatsJoueur(
                j.getId(), j.getNom(), j.getPrenom(), debut, fin,
                participation(matchsEquipe, parMatch, joueurId),
                tempsJeu(lignes, matchsEquipe.size()),
                somme(lignes, MatchJoueur::buts),
                somme(lignes, MatchJoueur::passesDecisives),
                somme(lignes, MatchJoueur::cartonsJaunes),
                (short) lignes.stream().filter(MatchJoueur::cartonRouge).count(),
                (short) lignes.stream().filter(MatchJoueur::cleanSheet).count(),
                postesOccupes(lignes),
                chargeVsJeu(joueurId, debut, fin, lignes),
                lignes);
    }

    // ── Temps de jeu ────────────────────────────────────────────────────────

    /**
     * Temps de port du capteur sur la séance liée au match, et seulement si le joueur est déclaré
     * entré en jeu : sans cette garde, un remplaçant qui s'échauffe capteur au dos accumulerait
     * des « minutes jouées » qu'il n'a pas jouées.
     */
    private Integer tempsGps(MatchPrepa m, UUID joueurId, MatchCompo c) {
        if (m.getSessionGpsId() == null || !c.isEntreEnJeu()) return null;
        return donneeGpsRepository.findByJoueurIdAndSeanceId(joueurId, m.getSessionGpsId())
                .map(g -> g.getDureeMinutes() != null ? g.getDureeMinutes().intValue() : null)
                .orElse(null);
    }

    /** Minutes déduites de la saisie du staff (cf. {@code MatchService.tempsJeuSaisi}). */
    private Integer tempsSaisi(MatchCompo c) {
        if (!c.isEntreEnJeu()) return 0;
        Integer entree = c.getMinuteEntree() != null ? c.getMinuteEntree().intValue() : null;
        Integer sortie = c.getMinuteSortie() != null ? c.getMinuteSortie().intValue() : null;
        if (entree == null && sortie == null) return null;
        int debut = entree != null ? entree : 0;
        int fin = sortie != null ? sortie : DUREE_MATCH_REFERENCE_MIN;
        return Math.max(fin - debut, 0);
    }

    /** Priorité actée : la saisie du staff prime, puis la fédération, puis le capteur. */
    private Integer tempsJeuRetenu(MatchCompo c, Integer gps) {
        Integer saisi = tempsSaisi(c);
        if (saisi != null) return saisi;
        if (c.getTempsJeuFederation() != null) return c.getTempsJeuFederation().intValue();
        return gps;
    }

    private String sourceTempsJeu(MatchCompo c, Integer gps) {
        if (tempsSaisi(c) != null) return "SAISIE";
        if (c.getTempsJeuFederation() != null) return "FEDERATION";
        return gps != null ? "GPS" : null;
    }

    private TempsJeu tempsJeu(List<MatchJoueur> lignes, int matchsEquipe) {
        int total = lignes.stream().mapToInt(l -> l.tempsJeu() != null ? l.tempsJeu() : 0).sum();
        long matchsJoues = lignes.stream().filter(l -> l.entreEnJeu() && l.tempsJeu() != null && l.tempsJeu() > 0).count();
        int possibles = matchsEquipe * DUREE_MATCH_REFERENCE_MIN;
        return new TempsJeu(
                total,
                matchsJoues > 0 ? (int) Math.round((double) total / matchsJoues) : 0,
                possibles,
                possibles > 0 ? (int) Math.round(100.0 * total / possibles) : 0,
                (int) lignes.stream().filter(l -> "SAISIE".equals(l.sourceTempsJeu())).count(),
                (int) lignes.stream().filter(l -> "FEDERATION".equals(l.sourceTempsJeu())).count(),
                (int) lignes.stream().filter(l -> "GPS".equals(l.sourceTempsJeu())).count());
    }

    // ── Participation ───────────────────────────────────────────────────────

    private Participation participation(List<MatchPrepa> matchsEquipe, Map<UUID, MatchCompo> parMatch, UUID joueurId) {
        int titulaire = 0, entre = 0, remplacantNonEntre = 0, reserve = 0, repos = 0, suspendu = 0, convocations = 0;
        int dispoNonRetenu = 0;
        List<Blessure> blessures = blessureRepository.findByJoueurIdOrderByDateBlessureDesc(joueurId);

        for (MatchPrepa m : matchsEquipe) {
            MatchCompo c = parMatch.get(m.getId());
            if (c == null) {
                // Non convoqué. Ne compte comme « disponible non retenu » que s'il était apte ce
                // jour-là : sinon un joueur blessé trois mois ressortirait écarté dix fois.
                if (!blesseLe(blessures, m.getDateMatch())) dispoNonRetenu++;
                continue;
            }
            convocations++;
            switch (c.getStatut() == null ? "" : c.getStatut()) {
                case "TITULAIRE" -> { titulaire++; entre++; }
                case "REMPLACANT" -> { if (c.isEntreEnJeu()) entre++; else remplacantNonEntre++; }
                case "RESERVE" -> reserve++;
                case "REPOS" -> repos++;
                case "SUSPENDU" -> suspendu++;
                default -> { }
            }
        }
        return new Participation(matchsEquipe.size(), convocations, titulaire, entre,
                remplacantNonEntre, reserve, repos, suspendu, dispoNonRetenu);
    }

    /** Le joueur était-il indisponible à cette date ? (blessure ouverte ou non encore refermée) */
    private boolean blesseLe(List<Blessure> blessures, LocalDate date) {
        if (date == null) return false;
        return blessures.stream().anyMatch(b -> {
            LocalDate debut = b.getDateBlessure();
            LocalDate retour = b.getDateRetourEffectif();
            if (debut == null || date.isBefore(debut)) return false;
            return retour == null || !date.isAfter(retour);
        });
    }

    // ── Polyvalence ─────────────────────────────────────────────────────────

    /**
     * Poste occupé déduit du placement en compo. La compo stocke des coordonnées relatives, pas un
     * poste : on en dérive une ligne de jeu, ce qui suffit à révéler la polyvalence réelle
     * (un ailier aligné arrière latéral trois fois de suite, par exemple).
     */
    private String posteDepuisPlacement(MatchCompo c) {
        if (!"TITULAIRE".equals(c.getStatut())) return null;
        BigDecimal y = c.getY();
        if (y == null || y.signum() == 0) return null;
        double v = y.doubleValue();
        if (v <= 0.20) return "Gardien";
        if (v <= 0.42) return "Défense";
        if (v <= 0.70) return "Milieu";
        return "Attaque";
    }

    private List<PosteOccupe> postesOccupes(List<MatchJoueur> lignes) {
        Map<String, Integer> parPoste = new LinkedHashMap<>();
        for (MatchJoueur l : lignes) {
            if (l.posteOccupe() == null) continue;
            parPoste.merge(l.posteOccupe(), 1, Integer::sum);
        }
        return parPoste.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .map(e -> new PosteOccupe(e.getKey(), e.getValue()))
                .toList();
    }

    // ── Charge d'entraînement ⇄ temps de jeu ────────────────────────────────

    /**
     * Le croisement différenciant : ce que le joueur encaisse à l'entraînement face à ce qu'il
     * obtient en compétition. La charge est le sRPE (RPE × durée), seule charge disponible côté
     * Java ; les séances liées à un match en sont exclues, sinon le match se compterait deux fois.
     */
    private ChargeVsJeu chargeVsJeu(UUID joueurId, LocalDate debut, LocalDate fin, List<MatchJoueur> lignes) {
        List<RpeSeance> rpes = rpeRepository.findByJoueurIdAndDateBetween(joueurId, debut, fin);
        double charge = rpes.stream().mapToDouble(r -> r.getCharge() != null ? r.getCharge() : 0).sum();
        int minutes = lignes.stream().mapToInt(l -> l.tempsJeu() != null ? l.tempsJeu() : 0).sum();

        Double ratio = charge > 0 ? Math.round(minutes / charge * 1000.0 * 10) / 10.0 : null;
        String lecture;
        if (charge <= 0) {
            lecture = "Aucune charge d'entraînement déclarée sur la période : le croisement n'est pas calculable.";
        } else if (minutes == 0) {
            lecture = "S'entraîne sans jouer : aucune minute de compétition sur la période, alors que la charge est bien présente.";
        } else if (ratio != null && ratio < 5) {
            lecture = "Charge d'entraînement élevée au regard du temps de jeu obtenu — situation qui use la motivation.";
        } else if (ratio != null && ratio > 20) {
            lecture = "Beaucoup de compétition pour peu d'entraînement — vigilance sur la récupération.";
        } else {
            lecture = "Équilibre entraînement / compétition dans les clous.";
        }
        return new ChargeVsJeu(Math.round(charge * 10) / 10.0, minutes, rpes.size(), ratio, lecture);
    }

    // ── Utilitaires ─────────────────────────────────────────────────────────

    private short somme(List<MatchJoueur> lignes, java.util.function.ToIntFunction<MatchJoueur> f) {
        return (short) lignes.stream().mapToInt(f).sum();
    }

    private StatsJoueur vide(Joueur j, LocalDate depuis) {
        LocalDate fin = horloge.today();
        LocalDate debut = depuis != null ? depuis : fin.minusYears(1);
        return new StatsJoueur(j.getId(), j.getNom(), j.getPrenom(), debut, fin,
                new Participation(0, 0, 0, 0, 0, 0, 0, 0, 0),
                new TempsJeu(0, 0, 0, 0, 0, 0, 0),
                (short) 0, (short) 0, (short) 0, (short) 0, (short) 0,
                List.of(),
                new ChargeVsJeu(0, 0, 0, null,
                        "Fiche non rattachée à une équipe : aucune compétition à agréger."),
                List.of());
    }
}
