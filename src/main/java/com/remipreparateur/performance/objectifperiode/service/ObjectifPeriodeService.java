package com.remipreparateur.performance.objectifperiode.service;

import com.remipreparateur.auth.rbac.FeatureModule;
import com.remipreparateur.auth.rbac.PermissionResolver;
import com.remipreparateur.club.pack.ClubModulesService;
import com.remipreparateur.performance.objectifperiode.dto.ObjectifPeriodeDtos.*;
import com.remipreparateur.performance.objectifperiode.entity.ObjectifPeriode;
import com.remipreparateur.performance.objectifperiode.entity.ObjectifPeriodeValeur;
import com.remipreparateur.performance.objectifperiode.repository.ObjectifPeriodeRepository;
import com.remipreparateur.performance.objectifperiode.repository.ObjectifPeriodeValeurRepository;
import com.remipreparateur.performance.objectifperiode.service.TrajectoireGenerator.Phase;
import com.remipreparateur.performance.objectifperiode.service.TrajectoireGenerator.PhaseRetenue;
import com.remipreparateur.performance.objectifperiode.service.TrajectoireGenerator.Repartition;
import com.remipreparateur.performance.referentiel.MetriqueCharge;
import com.remipreparateur.performance.referentiel.PosteReference;
import com.remipreparateur.performance.referentiel.entity.ReferentielObjectif;
import com.remipreparateur.performance.referentiel.entity.ReferentielObjectifValeur;
import com.remipreparateur.performance.referentiel.repository.ReferentielObjectifRepository;
import com.remipreparateur.performance.referentiel.repository.ReferentielObjectifValeurRepository;
import com.remipreparateur.performance.referentiel.service.ReferentielObjectifService;
import com.remipreparateur.saison.entity.PeriodeSaison;
import com.remipreparateur.saison.repository.PeriodeSaisonRepository;
import com.remipreparateur.shared.security.CurrentUserProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Instanciation d'un modèle d'objectif sur une période de la saison.
 *
 * <p>C'est ici que la FORME (le modèle, en pourcentages) rencontre l'ÉCHELLE (le référentiel, en
 * mètres) pour produire des valeurs concrètes. Deux formes de sortie selon le type de période :
 * <ul>
 *   <li><b>Préparation / reprise</b> → une trajectoire semaine par semaine, au niveau de l'équipe ;</li>
 *   <li><b>Compétition</b> → des fourchettes par poste, valables toute la période.</li>
 * </ul>
 *
 * <p>Une fois générées, les valeurs sont FIGÉES et éditables case par case : corriger le modèle
 * ensuite ne rattrape pas les instances. Un objectif déjà annoncé au groupe ne doit pas bouger
 * dans le dos du préparateur.
 */
@Service
public class ObjectifPeriodeService {

    private final ObjectifPeriodeRepository objectifRepository;
    private final ObjectifPeriodeValeurRepository valeurRepository;
    private final ModeleObjectifService modeleService;
    private final PeriodeSaisonRepository periodeRepository;
    private final ReferentielObjectifRepository referentielRepository;
    private final ReferentielObjectifValeurRepository referentielValeurRepository;
    private final ReferentielObjectifService referentielService;
    private final PermissionResolver permissionResolver;
    private final ClubModulesService clubModulesService;
    private final CurrentUserProvider currentUser;

    public ObjectifPeriodeService(ObjectifPeriodeRepository objectifRepository,
                                  ObjectifPeriodeValeurRepository valeurRepository,
                                  ModeleObjectifService modeleService,
                                  PeriodeSaisonRepository periodeRepository,
                                  ReferentielObjectifRepository referentielRepository,
                                  ReferentielObjectifValeurRepository referentielValeurRepository,
                                  ReferentielObjectifService referentielService,
                                  PermissionResolver permissionResolver,
                                  ClubModulesService clubModulesService,
                                  CurrentUserProvider currentUser) {
        this.objectifRepository = objectifRepository;
        this.valeurRepository = valeurRepository;
        this.modeleService = modeleService;
        this.periodeRepository = periodeRepository;
        this.referentielRepository = referentielRepository;
        this.referentielValeurRepository = referentielValeurRepository;
        this.referentielService = referentielService;
        this.permissionResolver = permissionResolver;
        this.clubModulesService = clubModulesService;
        this.currentUser = currentUser;
    }

    // ──────────────────────── Lecture ────────────────────────

    @Transactional(readOnly = true)
    public ObjectifPeriodeDetail detailParPeriode(UUID periodeId) {
        exigeModule();
        ObjectifPeriode o = objectifRepository.findByPeriodeId(periodeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Aucun objectif défini sur cette période."));
        exigeClub(o.getClubId());
        return new ObjectifPeriodeDetail(toResume(o), valeursDe(o.getId()));
    }

    /** État de toutes les périodes d'une équipe : « objectifs définis » ou « à définir ». */
    @Transactional(readOnly = true)
    public List<EtatPeriodeDto> etatPeriodes(UUID saisonId, UUID equipeId) {
        exigeModule();
        List<PeriodeSaison> periodes =
                periodeRepository.findBySaisonIdAndEquipeIdOrderByDateDebutAscOrdreAsc(saisonId, equipeId);
        if (periodes.isEmpty()) return List.of();
        Map<UUID, ObjectifPeriode> parPeriode = new LinkedHashMap<>();
        for (ObjectifPeriode o : objectifRepository.findByPeriodeIdIn(
                periodes.stream().map(PeriodeSaison::getId).toList())) {
            parPeriode.put(o.getPeriodeId(), o);
        }
        List<EtatPeriodeDto> res = new ArrayList<>();
        for (PeriodeSaison p : periodes) {
            ObjectifPeriode o = parPeriode.get(p.getId());
            res.add(new EtatPeriodeDto(p.getId(),
                    p.getLibelle() != null ? p.getLibelle() : p.getType(), p.getType(),
                    p.getDateDebut(), p.getDateFin(), nbSemaines(p),
                    o != null, o == null ? null : o.getId(),
                    o == null ? null : nomModele(o.getModeleId())));
        }
        return res;
    }

    // ──────────────────────── Génération ────────────────────────

    /** Aperçu SANS écriture : montre la répartition et l'avertissement avant de valider. */
    @Transactional(readOnly = true)
    public ApercuResponse apercu(InstancierRequest req) {
        exigeModule();
        PeriodeSaison periode = exigePeriode(req.periodeId());
        ReferentielObjectif referentiel = resoudreReferentiel(req.referentielId(), periode.getEquipeId());
        Genere g = generer(periode, req.modeleId(), referentiel);
        return new ApercuResponse(nbSemaines(periode), g.phasesResume(), g.avertissement(), g.valeurs());
    }

    /**
     * Instancie (ou ré-instancie) un modèle sur une période.
     *
     * <p>Une ré-instanciation ÉCRASE tout, y compris les cases retouchées à la main. L'appelant
     * doit avoir prévenu : le drapeau {@code modifieManuellement} existe précisément pour que
     * l'écran puisse compter ces cases et demander confirmation avant d'appeler ici.
     */
    @Transactional
    public ObjectifPeriodeDetail instancier(InstancierRequest req) {
        exigeModule();
        UUID clubId = clubActif();
        PeriodeSaison periode = exigePeriode(req.periodeId());
        ReferentielObjectif referentiel = resoudreReferentiel(req.referentielId(), periode.getEquipeId());
        Genere g = generer(periode, req.modeleId(), referentiel);

        ObjectifPeriode o = objectifRepository.findByPeriodeId(periode.getId())
                .orElseGet(ObjectifPeriode::new);
        o.setClubId(clubId);
        o.setPeriodeId(periode.getId());
        o.setModeleId(req.modeleId());
        o.setReferentielId(referentiel == null ? null : referentiel.getId());
        o.setPhasesResume(g.phasesResume());
        o.setAvertissement(g.avertissement());
        if (o.getCreePar() == null) o.setCreePar(currentUser.current().getId());
        o.setUpdatedAt(LocalDateTime.now());
        o = objectifRepository.save(o);

        valeurRepository.deleteByObjectifPeriodeId(o.getId());
        valeurRepository.flush();
        ecrire(o.getId(), g.valeurs(), false);
        return new ObjectifPeriodeDetail(toResume(o), valeursDe(o.getId()));
    }

    /** Enregistre les retouches manuelles du préparateur sur une instance existante. */
    @Transactional
    public ObjectifPeriodeDetail enregistrerValeurs(UUID periodeId, List<ValeurPeriodeDto> valeurs) {
        exigeModule();
        ObjectifPeriode o = objectifRepository.findByPeriodeId(periodeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Aucun objectif défini sur cette période."));
        exigeClub(o.getClubId());
        valeurRepository.deleteByObjectifPeriodeId(o.getId());
        valeurRepository.flush();
        ecrire(o.getId(), valeurs == null ? List.of() : valeurs, true);
        o.setUpdatedAt(LocalDateTime.now());
        objectifRepository.save(o);
        return new ObjectifPeriodeDetail(toResume(o), valeursDe(o.getId()));
    }

    @Transactional
    public void supprimer(UUID periodeId) {
        exigeModule();
        ObjectifPeriode o = objectifRepository.findByPeriodeId(periodeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Aucun objectif défini sur cette période."));
        exigeClub(o.getClubId());
        valeurRepository.deleteByObjectifPeriodeId(o.getId());
        objectifRepository.delete(o);
    }

    // ──────────────────────── Cœur de la génération ────────────────────────

    private record Genere(String phasesResume, String avertissement, List<ValeurPeriodeDto> valeurs) {}

    private Genere generer(PeriodeSaison periode, UUID modeleId, ReferentielObjectif referentiel) {
        if (modeleId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucun modèle sélectionné.");
        }
        if (referentiel == null) {
            // Un modèle est une forme en pourcentages : sans échelle, il ne produit rien. Le dire
            // vaut mieux que de générer une trajectoire à zéro que personne ne comprendra.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Aucun référentiel n'est adopté pour cette équipe : les objectifs sont exprimés "
                    + "en pourcentage d'une cible de référence, il en faut une.");
        }
        List<PhaseDto> phases = modeleService.phasesDe(modeleId);
        int nb = nbSemaines(periode);
        Repartition rep = TrajectoireGenerator.repartir(nb,
                phases.stream().map(p -> new Phase(p.nom(), p.poidsDuree())).toList());

        boolean competition = ModeleObjectifPhaseTypes.estCompetition(periode.getType());
        List<ValeurPeriodeDto> valeurs = competition
                ? genererCompetition(phases, rep, referentiel)
                : genererTrajectoire(periode, phases, rep, referentiel);

        String resume = rep.phases().stream()
                .map(p -> p.nom() + ":" + p.nbSemaines())
                .collect(java.util.stream.Collectors.joining("|"));
        return new Genere(resume, rep.avertissement(), valeurs);
    }

    /**
     * Trajectoire de préparation : une ligne par semaine et par métrique, au niveau de l'équipe.
     * La base est la moyenne des postes du référentiel sur le contexte SEMAINE — le document de
     * référence donne lui aussi sa progression de préparation au niveau du groupe, pas par poste.
     */
    private List<ValeurPeriodeDto> genererTrajectoire(PeriodeSaison periode, List<PhaseDto> phases,
                                                      Repartition rep, ReferentielObjectif referentiel) {
        Map<String, Integer> bases = basesEquipe(referentiel.getId());
        LocalDate lundi = periode.getDateDebut().with(DayOfWeek.MONDAY);
        List<ValeurPeriodeDto> res = new ArrayList<>();
        short noSemaine = 1;
        for (PhaseRetenue pr : rep.phases()) {
            PhaseDto phase = phases.get(pr.indexOrigine());
            for (int i = 0; i < pr.nbSemaines(); i++) {
                LocalDate lundiSemaine = lundi.plusWeeks(noSemaine - 1L);
                for (PhaseValeurDto pv : phase.valeurs()) {
                    MetriqueCharge m = MetriqueCharge.parCode(pv.metrique());
                    if (m == null) continue;
                    double pct = TrajectoireGenerator.pctSemaine(i, pr.nbSemaines(),
                            pv.pctDebut(), pv.pctFin());
                    Integer valeur = appliquer(m, bases.get(m.getCode()), pct);
                    if (valeur == null) continue;
                    res.add(new ValeurPeriodeDto(noSemaine, lundiSemaine, null, m.getCode(),
                            valeur, valeur, pv.priorite(), phase.nom(), false));
                }
                noSemaine++;
            }
        }
        return res;
    }

    /**
     * Cibles de compétition : une fourchette par poste, issue de la fourchette du référentiel.
     * On ne déroule pas les semaines — en championnat, la cible est un régime, pas une montée.
     * Les phases servent alors uniquement de niveau (une phase unique à 100 %, le plus souvent).
     */
    private List<ValeurPeriodeDto> genererCompetition(List<PhaseDto> phases, Repartition rep,
                                                      ReferentielObjectif referentiel) {
        if (rep.phases().isEmpty()) return List.of();
        PhaseDto phase = phases.get(rep.phases().get(0).indexOrigine());
        List<ReferentielObjectifValeur> refs = referentielValeurRepository
                .findByReferentielIdAndContexte(referentiel.getId(),
                        ReferentielObjectifValeur.CONTEXTE_SEMAINE);
        List<ValeurPeriodeDto> res = new ArrayList<>();
        for (PhaseValeurDto pv : phase.valeurs()) {
            MetriqueCharge m = MetriqueCharge.parCode(pv.metrique());
            if (m == null) continue;
            double pct = (pv.pctDebut() + pv.pctFin()) / 2.0;
            for (ReferentielObjectifValeur r : refs) {
                if (!r.getMetrique().equals(m.getCode())) continue;
                if (PosteReference.parCode(r.getPoste()) == null) continue;
                Integer min = appliquer(m, r.getValeurMin(), pct);
                Integer max = appliquer(m, r.getValeurMax(), pct);
                if (min == null && max == null) continue;
                res.add(new ValeurPeriodeDto(null, null, r.getPoste(), m.getCode(),
                        min, max, pv.priorite(), phase.nom(), false));
            }
        }
        return res;
    }

    /**
     * Applique un pourcentage à une base.
     *
     * <p>Exception des métriques d'EXPOSITION : le pourcentage EST la cible (% du record
     * personnel), il ne multiplie rien. « 109 % de 90 % » n'a aucun sens ; « atteindre 94 % de
     * son record cette semaine » en a un.
     */
    private static Integer appliquer(MetriqueCharge m, Integer base, double pct) {
        if (m.getNature() == MetriqueCharge.Nature.EXPOSITION) {
            return (int) Math.round(Math.min(100.0, pct));
        }
        if (base == null) return null;
        return (int) Math.round(base * pct / 100.0 / 10.0) * 10;   // arrondi à la dizaine
    }

    /** Moyenne des postes, métrique par métrique, sur le contexte SEMAINE du référentiel. */
    private Map<String, Integer> basesEquipe(UUID referentielId) {
        Map<String, int[]> cumul = new LinkedHashMap<>();   // [somme, compte]
        for (ReferentielObjectifValeur v : referentielValeurRepository
                .findByReferentielIdAndContexte(referentielId, ReferentielObjectifValeur.CONTEXTE_SEMAINE)) {
            Integer pivot = v.valeurPivot();
            if (pivot == null || PosteReference.parCode(v.getPoste()) == null) continue;
            int[] c = cumul.computeIfAbsent(v.getMetrique(), k -> new int[2]);
            c[0] += pivot;
            c[1]++;
        }
        Map<String, Integer> bases = new LinkedHashMap<>();
        cumul.forEach((metrique, c) -> { if (c[1] > 0) bases.put(metrique, c[0] / c[1]); });
        return bases;
    }

    // ──────────────────────── Helpers ────────────────────────

    /** Nombre de semaines ISO couvertes par la période, lundi de début inclus. */
    private static int nbSemaines(PeriodeSaison p) {
        LocalDate lundi = p.getDateDebut().with(DayOfWeek.MONDAY);
        long jours = ChronoUnit.DAYS.between(lundi, p.getDateFin());
        return (int) Math.max(1, jours / 7 + 1);
    }

    private void ecrire(UUID objectifId, List<ValeurPeriodeDto> valeurs, boolean manuel) {
        List<ObjectifPeriodeValeur> lignes = new ArrayList<>();
        for (ValeurPeriodeDto v : valeurs) {
            if (MetriqueCharge.parCode(v.metrique()) == null) continue;
            ObjectifPeriodeValeur l = new ObjectifPeriodeValeur();
            l.setObjectifPeriodeId(objectifId);
            l.setNoSemaine(v.noSemaine());
            l.setDateLundi(v.dateLundi());
            l.setPoste(v.poste());
            l.setMetrique(v.metrique());
            l.setValeurMin(v.valeurMin());
            l.setValeurMax(v.valeurMax());
            l.setPriorite(com.remipreparateur.performance.referentiel.PrioriteMetrique
                    .parNom(v.priorite()).name());
            l.setPhaseNom(v.phaseNom());
            l.setModifieManuellement(manuel && v.modifieManuellement());
            lignes.add(l);
        }
        valeurRepository.saveAll(lignes);
    }

    private List<ValeurPeriodeDto> valeursDe(UUID objectifId) {
        return valeurRepository.findByObjectifPeriodeId(objectifId).stream()
                .sorted(java.util.Comparator
                        .comparing((ObjectifPeriodeValeur v) -> v.getNoSemaine() == null ? 0 : v.getNoSemaine())
                        .thenComparing(v -> v.getPoste() == null ? "" : v.getPoste())
                        .thenComparingInt(v -> {
                            MetriqueCharge m = MetriqueCharge.parCode(v.getMetrique());
                            return m == null ? 99 : m.getOrdre();
                        }))
                .map(v -> new ValeurPeriodeDto(v.getNoSemaine(), v.getDateLundi(), v.getPoste(),
                        v.getMetrique(), v.getValeurMin(), v.getValeurMax(), v.getPriorite(),
                        v.getPhaseNom(), v.isModifieManuellement()))
                .toList();
    }

    private ReferentielObjectif resoudreReferentiel(UUID explicite, UUID equipeId) {
        UUID clubId = clubActif();
        if (explicite != null) {
            return referentielRepository.findById(explicite).orElse(null);
        }
        return referentielService.resoudre(clubId, equipeId).orElse(null);
    }

    private ObjectifPeriodeResume toResume(ObjectifPeriode o) {
        Optional<PeriodeSaison> periode = periodeRepository.findById(o.getPeriodeId());
        String refNom = o.getReferentielId() == null ? null
                : referentielRepository.findById(o.getReferentielId())
                    .map(ReferentielObjectif::getNom).orElse(null);
        return new ObjectifPeriodeResume(o.getId(), o.getPeriodeId(),
                periode.map(p -> p.getLibelle() != null ? p.getLibelle() : p.getType()).orElse(null),
                periode.map(PeriodeSaison::getType).orElse(null),
                periode.map(PeriodeSaison::getDateDebut).orElse(null),
                periode.map(PeriodeSaison::getDateFin).orElse(null),
                periode.map(ObjectifPeriodeService::nbSemaines).orElse(0),
                o.getModeleId(), nomModele(o.getModeleId()),
                o.getReferentielId(), refNom,
                o.getPhasesResume(), o.getAvertissement(), o.getUpdatedAt());
    }

    private String nomModele(UUID modeleId) {
        if (modeleId == null) return null;
        try {
            return modeleService.detail(modeleId).entete().nom();
        } catch (ResponseStatusException e) {
            return null;   // modèle supprimé depuis : l'instance survit, elle porte ses valeurs
        }
    }

    private PeriodeSaison exigePeriode(UUID periodeId) {
        if (periodeId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Période manquante");
        }
        return periodeRepository.findById(periodeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Période introuvable"));
    }

    private void exigeClub(UUID clubId) {
        if (!clubActif().equals(clubId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Objectif introuvable");
        }
    }

    private UUID clubActif() {
        UUID clubId = permissionResolver.clubActif(currentUser.current());
        if (clubId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Aucun club actif");
        }
        return clubId;
    }

    private void exigeModule() {
        UUID clubId = permissionResolver.clubActif(currentUser.current());
        if (clubId == null) return;
        if (!clubModulesService.modulesActifs(clubId).contains(FeatureModule.OBJECTIFS_PERFORMANCE.getCode())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Le module « " + FeatureModule.OBJECTIFS_PERFORMANCE.getLibelle()
                            + " » n'est pas activé pour votre club.");
        }
    }

    /** Types de période produisant des cibles par poste plutôt qu'une trajectoire hebdomadaire. */
    static final class ModeleObjectifPhaseTypes {
        private ModeleObjectifPhaseTypes() {}
        static boolean estCompetition(String typePeriode) {
            return "COMPETITION".equalsIgnoreCase(typePeriode);
        }
    }
}
