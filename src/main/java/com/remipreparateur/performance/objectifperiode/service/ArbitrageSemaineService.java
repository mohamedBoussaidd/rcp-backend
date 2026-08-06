package com.remipreparateur.performance.objectifperiode.service;

import com.remipreparateur.auth.rbac.FeatureModule;
import com.remipreparateur.auth.rbac.PermissionResolver;
import com.remipreparateur.club.pack.ClubModulesService;
import com.remipreparateur.performance.objectifperiode.dto.ArbitrageDtos.*;
import com.remipreparateur.performance.objectifperiode.entity.ArbitrageSemaine;
import com.remipreparateur.performance.objectifperiode.entity.ArbitrageSemaineReport;
import com.remipreparateur.performance.objectifperiode.entity.ObjectifPeriode;
import com.remipreparateur.performance.objectifperiode.entity.ObjectifPeriodeValeur;
import com.remipreparateur.performance.objectifperiode.repository.ArbitrageSemaineReportRepository;
import com.remipreparateur.performance.objectifperiode.repository.ArbitrageSemaineRepository;
import com.remipreparateur.performance.objectifperiode.repository.ObjectifPeriodeRepository;
import com.remipreparateur.performance.objectifperiode.repository.ObjectifPeriodeValeurRepository;
import com.remipreparateur.performance.referentiel.MetriqueCharge;
import com.remipreparateur.performance.referentiel.entity.ReferentielObjectif;
import com.remipreparateur.performance.referentiel.entity.ReferentielObjectifValeur;
import com.remipreparateur.performance.referentiel.repository.ReferentielObjectifValeurRepository;
import com.remipreparateur.performance.referentiel.service.ReferentielObjectifService;
import com.remipreparateur.saison.entity.PeriodeSaison;
import com.remipreparateur.saison.repository.PeriodeSaisonRepository;
import com.remipreparateur.shared.security.CurrentUserProvider;
import com.remipreparateur.shared.security.ScopeResolver;
import com.remipreparateur.tactical.match.entity.MatchPrepa;
import com.remipreparateur.tactical.match.repository.MatchPrepaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Arbitrage d'une semaine à deux matchs.
 *
 * <p>Le piège que cette classe existe pour éviter : la cible hebdomadaire du référentiel INCLUT
 * le match. Une deuxième rencontre ne relève donc pas la semaine toute seule — elle mange la part
 * d'entraînement. Sans arbitrage explicite, le préparateur lit « 34 km » et croit disposer de
 * 34 km d'entraînement alors qu'il lui en reste 14.
 *
 * <p>Trois réponses, aucune bonne dans l'absolu :
 * <ul>
 *   <li>{@code ALLEGER} (défaut) — cible inchangée, l'entraînement encaisse. Aucun delta écrit :
 *       l'effet est une dérivation d'affichage.</li>
 *   <li>{@code ASSUMER} — cible relevée d'un match, l'entraînement ne bouge pas.</li>
 *   <li>{@code RELISSER} — cible réduite d'un match, la différence partant à parts égales sur les
 *       deux semaines suivantes de la MÊME période.</li>
 * </ul>
 *
 * <p>Ces décisions ne réécrivent jamais l'objectif de période : elles produisent des deltas
 * (cf. {@link ArbitrageSemaineReport}). Le prescrit reste lisible sous l'ajustement, et retirer
 * l'arbitrage rétablit la trajectoire d'origine sans rien régénérer.
 */
@Service
public class ArbitrageSemaineService {

    /** Nombre de semaines qui reçoivent un report. Borné pour que l'écart reste visible. */
    private static final int SEMAINES_DE_REPORT = 2;

    private final ArbitrageSemaineRepository arbitrageRepository;
    private final ArbitrageSemaineReportRepository reportRepository;
    private final MatchPrepaRepository matchRepository;
    private final PeriodeSaisonRepository periodeRepository;
    private final ObjectifPeriodeRepository objectifRepository;
    private final ObjectifPeriodeValeurRepository valeurRepository;
    private final ReferentielObjectifService referentielService;
    private final ReferentielObjectifValeurRepository referentielValeurRepository;
    private final PermissionResolver permissionResolver;
    private final ClubModulesService clubModulesService;
    private final CurrentUserProvider currentUser;
    private final ScopeResolver scopeResolver;

    public ArbitrageSemaineService(ArbitrageSemaineRepository arbitrageRepository,
                                   ArbitrageSemaineReportRepository reportRepository,
                                   MatchPrepaRepository matchRepository,
                                   PeriodeSaisonRepository periodeRepository,
                                   ObjectifPeriodeRepository objectifRepository,
                                   ObjectifPeriodeValeurRepository valeurRepository,
                                   ReferentielObjectifService referentielService,
                                   ReferentielObjectifValeurRepository referentielValeurRepository,
                                   PermissionResolver permissionResolver,
                                   ClubModulesService clubModulesService,
                                   CurrentUserProvider currentUser,
                                   ScopeResolver scopeResolver) {
        this.arbitrageRepository = arbitrageRepository;
        this.reportRepository = reportRepository;
        this.matchRepository = matchRepository;
        this.periodeRepository = periodeRepository;
        this.objectifRepository = objectifRepository;
        this.valeurRepository = valeurRepository;
        this.referentielService = referentielService;
        this.referentielValeurRepository = referentielValeurRepository;
        this.permissionResolver = permissionResolver;
        this.clubModulesService = clubModulesService;
        this.currentUser = currentUser;
        this.scopeResolver = scopeResolver;
    }

    // ── Lecture ────────────────────────────────────────────────────────────

    /**
     * État d'une semaine : ce que dit le calendrier, ce qui a été décidé, ce que ça a produit.
     * `dateLundi` est normalisé sur le lundi ISO — un dimanche envoyé par le front ne doit pas
     * créer une deuxième semaine fantôme à côté de la vraie.
     */
    @Transactional(readOnly = true)
    public SemaineArbitrageDto etat(LocalDate dateSemaine) {
        exigeModule();
        UUID equipeId = scopeResolver.equipeActiveUnique();   // 409 si contexte multi-équipes
        return construire(equipeId, lundiDe(dateSemaine));
    }

    // ── Écriture ───────────────────────────────────────────────────────────

    /**
     * Enregistre (ou remplace) la décision d'une semaine et recalcule ses deltas.
     *
     * <p>Les reports précédents sont supprimés avant recalcul : changer d'avis ne doit jamais
     * empiler deux ajustements sur les mêmes semaines.
     */
    @Transactional
    public SemaineArbitrageDto enregistrer(ArbitrageRequest req) {
        exigeModule();
        UUID equipeId = scopeResolver.equipeActiveUnique();
        UUID clubId = permissionResolver.clubActif(currentUser.current());
        if (req == null || req.dateLundi() == null || req.choix() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Semaine et choix obligatoires.");
        }
        String choix = req.choix().toUpperCase();
        if (!List.of("ALLEGER", "ASSUMER", "RELISSER").contains(choix)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choix inconnu : " + req.choix());
        }
        LocalDate lundi = lundiDe(req.dateLundi());

        List<MatchPrepa> matchs = matchsDeLaSemaine(equipeId, lundi);
        if (matchs.size() < 2) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cette semaine ne compte pas deux matchs : il n'y a rien à arbitrer.");
        }

        // Chiffrer un report demande de savoir ce que « coûte » un match. Cette valeur vient du
        // référentiel (contexte MATCH) : c'est lui qui porte l'échelle. Sans référentiel adopté,
        // ALLEGER reste possible — il ne chiffre rien — mais pas les deux autres.
        Map<String, Integer> coutMatch = coutDunMatch(clubId, equipeId);
        if (coutMatch.isEmpty() && !"ALLEGER".equals(choix)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Aucun référentiel adopté pour cette équipe : la charge d'un match est inconnue, "
                            + "seul l'allègement de l'entraînement est possible. "
                            + "Adoptez un référentiel dans Performance › Objectifs de performance.");
        }

        ArbitrageSemaine arb = arbitrageRepository.findByEquipeIdAndDateLundi(equipeId, lundi)
                .orElseGet(ArbitrageSemaine::new);
        arb.setClubId(clubId);
        arb.setEquipeId(equipeId);
        arb.setDateLundi(lundi);
        arb.setChoix(choix);
        arb.setNbMatchs((short) matchs.size());
        arb.setNote(req.note());
        arb.setCreePar(currentUser.current().getId());
        arb.setUpdatedAt(LocalDateTime.now());
        arb = arbitrageRepository.save(arb);

        reportRepository.deleteByArbitrageId(arb.getId());
        List<ArbitrageSemaineReport> reports = calculerReports(arb, equipeId, lundi, choix, coutMatch);
        if (!reports.isEmpty()) reportRepository.saveAll(reports);

        return construire(equipeId, lundi);
    }

    /** Retire la décision : les deltas partent avec elle et la trajectoire d'origine revient. */
    @Transactional
    public SemaineArbitrageDto supprimer(LocalDate dateSemaine) {
        exigeModule();
        UUID equipeId = scopeResolver.equipeActiveUnique();
        LocalDate lundi = lundiDe(dateSemaine);
        arbitrageRepository.findByEquipeIdAndDateLundi(equipeId, lundi).ifPresent(a -> {
            reportRepository.deleteByArbitrageId(a.getId());
            arbitrageRepository.delete(a);
        });
        return construire(equipeId, lundi);
    }

    // ── Cœur du calcul ─────────────────────────────────────────────────────

    /**
     * Deltas produits par une décision, métrique par métrique.
     *
     * <p>Deux règles héritées du cadrage : on ne touche jamais à une métrique marquée
     * {@code INTOUCHABLE} sur la phase en cours (on sacrifie du volume, jamais l'exposition haute
     * vitesse — risque ischio), et l'exposition à la vitesse max ne se reporte pas du tout : c'est
     * un pic en pourcentage du record personnel, pas un stock qu'on déplace d'une semaine à l'autre.
     */
    private List<ArbitrageSemaineReport> calculerReports(ArbitrageSemaine arb, UUID equipeId,
                                                         LocalDate lundi, String choix,
                                                         Map<String, Integer> coutMatch) {
        List<ArbitrageSemaineReport> reports = new ArrayList<>();
        if ("ALLEGER".equals(choix)) return reports;   // aucun delta : la semaine ne bouge pas

        Map<String, String> priorites = prioritesDeLaSemaine(equipeId, lundi);
        List<LocalDate> cibles = "RELISSER".equals(choix) ? semainesCibles(equipeId, lundi) : List.of();
        if ("RELISSER".equals(choix) && cibles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La période se termine : aucune semaine ne peut recevoir le report. "
                            + "Choisissez d'alléger l'entraînement ou d'assumer la charge.");
        }

        for (MetriqueCharge m : MetriqueCharge.toutes()) {
            if (!m.estCumulative()) continue;                                  // un pic ne se reporte pas
            if ("INTOUCHABLE".equals(priorites.get(m.getCode()))) continue;    // jamais sacrifiée
            Integer cout = coutMatch.get(m.getCode());
            if (cout == null || cout <= 0) continue;

            if ("ASSUMER".equals(choix)) {
                reports.add(report(arb.getId(), lundi, m.getCode(), cout));
                continue;
            }
            // RELISSER : la semaine perd la charge d'un match, les suivantes se la partagent.
            reports.add(report(arb.getId(), lundi, m.getCode(), -cout));
            int[] parts = repartir(cout, cibles.size());
            for (int i = 0; i < cibles.size(); i++) {
                reports.add(report(arb.getId(), cibles.get(i), m.getCode(), parts[i]));
            }
        }
        return reports;
    }

    /**
     * Ce que « coûte » un match, par métrique, en unité de la métrique. Moyenne des postes du
     * référentiel adopté (contexte MATCH) : un arbitrage porte sur l'équipe, pas sur un poste.
     * Map vide si aucun référentiel n'est adopté.
     */
    private Map<String, Integer> coutDunMatch(UUID clubId, UUID equipeId) {
        Optional<ReferentielObjectif> ref = referentielService.resoudre(clubId, equipeId);
        if (ref.isEmpty()) return Map.of();

        Map<String, List<Integer>> parMetrique = new HashMap<>();
        for (ReferentielObjectifValeur v : referentielValeurRepository
                .findByReferentielIdAndContexte(ref.get().getId(), ReferentielObjectifValeur.CONTEXTE_MATCH)) {
            Integer valeur = milieu(v.getValeurMin(), v.getValeurMax());
            if (valeur != null) {
                parMetrique.computeIfAbsent(v.getMetrique(), k -> new ArrayList<>()).add(valeur);
            }
        }
        Map<String, Integer> res = new HashMap<>();
        parMetrique.forEach((metrique, valeurs) -> res.put(metrique,
                (int) Math.round(valeurs.stream().mapToInt(Integer::intValue).average().orElse(0))));
        return res;
    }

    /**
     * Les deux semaines suivantes qui restent dans la même période. Le report ne franchit jamais
     * la fin d'une période : la suivante a ses propres phases, y déverser du volume détruirait la
     * décharge d'avant-match ou la reprise progressive.
     */
    private List<LocalDate> semainesCibles(UUID equipeId, LocalDate lundi) {
        LocalDate fin = periodeDe(equipeId, lundi).map(PeriodeSaison::getDateFin).orElse(null);
        List<LocalDate> cibles = new ArrayList<>();
        for (int i = 1; i <= SEMAINES_DE_REPORT; i++) {
            LocalDate candidate = lundi.plusWeeks(i);
            if (fin != null && candidate.isAfter(fin)) break;
            cibles.add(candidate);
        }
        return cibles;
    }

    /** Priorité par métrique telle que définie sur la semaine dans l'objectif de période. */
    private Map<String, String> prioritesDeLaSemaine(UUID equipeId, LocalDate lundi) {
        Optional<PeriodeSaison> periode = periodeDe(equipeId, lundi);
        if (periode.isEmpty()) return Map.of();
        Optional<ObjectifPeriode> objectif = objectifRepository.findByPeriodeId(periode.get().getId());
        if (objectif.isEmpty()) return Map.of();

        Map<String, String> res = new HashMap<>();
        for (ObjectifPeriodeValeur v : valeurRepository.findByObjectifPeriodeId(objectif.get().getId())) {
            // Trajectoire : on ne retient que la semaine concernée. Cibles par poste (compétition) :
            // la priorité est la même pour toute la période, donc n'importe quelle ligne fait foi.
            if (v.getDateLundi() == null || lundi.equals(v.getDateLundi())) {
                res.put(v.getMetrique(), v.getPriorite());
            }
        }
        return res;
    }

    private Optional<PeriodeSaison> periodeDe(UUID equipeId, LocalDate date) {
        return periodeRepository
                .findByEquipeIdAndDateDebutLessThanEqualAndDateFinGreaterThanEqualOrderByDateDebutAsc(
                        equipeId, date, date).stream().findFirst();
    }

    // ── Assemblage de la réponse ───────────────────────────────────────────

    private SemaineArbitrageDto construire(UUID equipeId, LocalDate lundi) {
        UUID clubId = permissionResolver.clubActif(currentUser.current());
        List<MatchPrepa> matchs = matchsDeLaSemaine(equipeId, lundi);
        Optional<ArbitrageSemaine> arb = arbitrageRepository.findByEquipeIdAndDateLundi(equipeId, lundi);

        List<ReportDto> reports = arb.map(a -> reportRepository.findByArbitrageId(a.getId()).stream()
                        .map(r -> new ReportDto(r.getDateLundiCible(), r.getMetrique(), r.getDelta()))
                        .sorted(Comparator.comparing(ReportDto::dateLundiCible)
                                .thenComparing(ReportDto::metrique))
                        .toList())
                .orElse(List.of());

        Map<String, Integer> cout = coutDunMatch(clubId, equipeId);
        String avertissement = null;
        if (arb.isPresent() && arb.get().getNbMatchs() != null
                && arb.get().getNbMatchs() != matchs.size()) {
            avertissement = "Le calendrier a changé depuis cette décision : "
                    + arb.get().getNbMatchs() + " match(s) au moment de l'arbitrage, "
                    + matchs.size() + " aujourd'hui. Revoyez le choix.";
        }

        return new SemaineArbitrageDto(
                equipeId,
                lundi,
                matchs.size(),
                matchs.stream().map(MatchPrepa::getDateMatch).filter(java.util.Objects::nonNull).sorted().toList(),
                arb.map(ArbitrageSemaine::getChoix).orElse(null),
                arb.map(ArbitrageSemaine::getNote).orElse(null),
                periodeDe(equipeId, lundi).map(PeriodeSaison::getDateFin).orElse(null),
                reports,
                semainesCibles(equipeId, lundi),
                !cout.isEmpty(),
                cout.get(MetriqueCharge.DISTANCE_TOTALE.getCode()),
                avertissement);
    }

    private List<MatchPrepa> matchsDeLaSemaine(UUID equipeId, LocalDate lundi) {
        return matchRepository.findByEquipeIdAndDateMatchBetween(equipeId, lundi, lundi.plusDays(6));
    }

    // ── Utilitaires ────────────────────────────────────────────────────────

    /**
     * Répartit une charge sur n semaines. Le dernier reçoit le reste de la division entière, si
     * bien que la somme rendue vaut EXACTEMENT `cout` : c'est ce qui garantit qu'un relissage ne
     * crée ni ne perd de charge en chemin (invariant vérifié par le test).
     */
    public static int[] repartir(int cout, int nbCibles) {
        if (nbCibles <= 0) return new int[0];
        int[] parts = new int[nbCibles];
        int part = cout / nbCibles;
        for (int i = 0; i < nbCibles - 1; i++) parts[i] = part;
        parts[nbCibles - 1] = cout - part * (nbCibles - 1);
        return parts;
    }

    private ArbitrageSemaineReport report(UUID arbitrageId, LocalDate cible, String metrique, int delta) {
        ArbitrageSemaineReport r = new ArbitrageSemaineReport();
        r.setArbitrageId(arbitrageId);
        r.setDateLundiCible(cible);
        r.setMetrique(metrique);
        r.setDelta(delta);
        return r;
    }

    private static Integer milieu(Integer min, Integer max) {
        if (min == null && max == null) return null;
        if (min == null) return max;
        if (max == null) return min;
        return (min + max) / 2;
    }

    /** Toute date d'une semaine ramenée à son lundi : une seule ancre, celle du panneau hebdo. */
    private static LocalDate lundiDe(LocalDate date) {
        return date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private void exigeModule() {
        UUID clubId = permissionResolver.clubActif(currentUser.current());
        if (clubId == null) return;   // super-admin hors contexte : rien à verrouiller
        if (!clubModulesService.modulesActifs(clubId).contains(FeatureModule.OBJECTIFS_PERFORMANCE.getCode())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Le module « " + FeatureModule.OBJECTIFS_PERFORMANCE.getLibelle()
                            + " » n'est pas activé pour votre club.");
        }
    }
}
