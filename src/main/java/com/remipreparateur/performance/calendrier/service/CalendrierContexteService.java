package com.remipreparateur.performance.calendrier.service;

import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.medical.wellness.entity.WellnessQuotidien;
import com.remipreparateur.medical.wellness.repository.WellnessQuotidienRepository;
import com.remipreparateur.notification.entity.NotifConfigEquipe;
import com.remipreparateur.notification.repository.NotifConfigEquipeRepository;
import com.remipreparateur.performance.calendrier.dto.CalendrierDtos.Anniversaire;
import com.remipreparateur.performance.calendrier.dto.CalendrierDtos.ContexteCalendrier;
import com.remipreparateur.performance.calendrier.dto.CalendrierDtos.JourRessenti;
import com.remipreparateur.performance.calendrier.dto.CalendrierDtos.SeanceRessenti;
import com.remipreparateur.performance.rpe.entity.RpeSeance;
import com.remipreparateur.performance.rpe.repository.RpeSeanceRepository;
import com.remipreparateur.performance.seance.entity.Seance;
import com.remipreparateur.performance.seance.repository.SeanceRepository;
import com.remipreparateur.saison.service.AppartenanceService;
import com.remipreparateur.shared.security.Scope;
import com.remipreparateur.shared.security.ScopeResolver;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Contexte affiché PAR-DESSUS le calendrier : jours où le ressenti est attendu, retours sRPE
 * par séance, anniversaires. Une seule requête HTTP pour une période, en lecture pure.
 *
 * <p>Deux points de vue :
 * <ul>
 *   <li><b>staff</b> — agrégats d'équipe (combien ont rempli, quel RPE moyen) ;</li>
 *   <li><b>joueur</b> — son propre état (ai-je rempli ? ai-je noté cette séance ?).</li>
 * </ul>
 * Les anniversaires sont identiques dans les deux cas : ni date de naissance ni âge ne sortent
 * du serveur, seulement le jour et le mois.
 */
@Service
public class CalendrierContexteService {

    /** Cadence par défaut si l'équipe n'a pas encore de configuration : tous les jours (V74). */
    private static final String JOURS_PAR_DEFAUT = "1,2,3,4,5,6,7";

    /** Garde-fou : au-delà, on refuse d'agréger (le calendrier n'affiche jamais plus d'un mois +/-). */
    private static final int JOURS_MAX = 120;

    private final WellnessQuotidienRepository wellnessRepository;
    private final RpeSeanceRepository rpeRepository;
    private final SeanceRepository seanceRepository;
    private final NotifConfigEquipeRepository notifConfigRepository;
    private final JoueurRepository joueurRepository;
    private final AppartenanceService appartenance;
    private final ScopeResolver scopeResolver;

    public CalendrierContexteService(WellnessQuotidienRepository wellnessRepository,
                                     RpeSeanceRepository rpeRepository,
                                     SeanceRepository seanceRepository,
                                     NotifConfigEquipeRepository notifConfigRepository,
                                     JoueurRepository joueurRepository,
                                     AppartenanceService appartenance,
                                     ScopeResolver scopeResolver) {
        this.wellnessRepository = wellnessRepository;
        this.rpeRepository = rpeRepository;
        this.seanceRepository = seanceRepository;
        this.notifConfigRepository = notifConfigRepository;
        this.joueurRepository = joueurRepository;
        this.appartenance = appartenance;
        this.scopeResolver = scopeResolver;
    }

    /** Vue staff : agrégats sur la portée d'équipe de l'utilisateur. */
    public ContexteCalendrier pourStaff(LocalDate debut, LocalDate fin) {
        LocalDate[] bornes = borner(debut, fin);
        Scope scope = scopeResolver.resolve();

        List<WellnessQuotidien> wellness = scope.all()
                ? wellnessRepository.findByDateBetween(bornes[0], bornes[1])
                : scope.none() ? List.of()
                        : wellnessRepository.findByEquipeIdInAndDateBetween(scope.equipeIds(), bornes[0], bornes[1]);
        List<RpeSeance> rpe = scope.all()
                ? rpeRepository.findByDateBetween(bornes[0], bornes[1])
                : scope.none() ? List.of()
                        : rpeRepository.findByEquipeIdInAndDateBetween(scope.equipeIds(), bornes[0], bornes[1]);

        Set<Integer> joursAttendus = joursAttendus(scope);
        int effectif = effectifDeReference(scope);

        return new ContexteCalendrier(
                jours(bornes, wellness, joursAttendus, effectif, null),
                seances(rpe, null),
                anniversaires(bornes));
    }

    /** Vue joueur : son propre état de remplissage, pas les agrégats de l'équipe. */
    public ContexteCalendrier pourJoueur(UUID joueurId, LocalDate debut, LocalDate fin) {
        LocalDate[] bornes = borner(debut, fin);
        List<WellnessQuotidien> wellness =
                wellnessRepository.findByJoueurIdAndDateBetween(joueurId, bornes[0], bornes[1]);
        List<RpeSeance> rpe = rpeRepository.findByJoueurIdAndDateBetween(joueurId, bornes[0], bornes[1]);

        UUID equipe = appartenance.equipePrincipale(joueurId);
        Set<Integer> joursAttendus = equipe == null
                ? joursDe(JOURS_PAR_DEFAUT)
                : joursAttendus(Scope.equipes(List.of(equipe)));

        // Effectif à 0 : côté joueur le « 3/18 » n'a pas de sens, seul son propre état compte.
        return new ContexteCalendrier(
                jours(bornes, wellness, joursAttendus, 0, joueurId),
                seances(rpe, joueurId),
                anniversaires(bornes));
    }

    // ──────────────────────────── Assemblage ────────────────────────────

    private List<JourRessenti> jours(LocalDate[] bornes, List<WellnessQuotidien> wellness,
                                     Set<Integer> joursAttendus, int effectif, UUID moi) {
        Map<LocalDate, List<WellnessQuotidien>> parDate = wellness.stream()
                .collect(Collectors.groupingBy(WellnessQuotidien::getDate));

        List<JourRessenti> out = new ArrayList<>();
        for (LocalDate d = bornes[0]; !d.isAfter(bornes[1]); d = d.plusDays(1)) {
            List<WellnessQuotidien> duJour = parDate.getOrDefault(d, List.of());
            boolean moiFait = moi != null && duJour.stream().anyMatch(w -> moi.equals(w.getJoueurId()));
            out.add(new JourRessenti(
                    d,
                    joursAttendus.contains(d.getDayOfWeek().getValue()),
                    duJour.size(),
                    effectif,
                    moiFait));
        }
        return out;
    }

    private List<SeanceRessenti> seances(List<RpeSeance> rpe, UUID moi) {
        Map<UUID, List<RpeSeance>> parSeance = rpe.stream().collect(Collectors.groupingBy(RpeSeance::getSeanceId));
        // Durées PLANIFIÉES, en une requête : sans elles, impossible de distinguer une séance
        // écourtée d'une séance normale — c'est pourtant le signal le plus actionnable du lot.
        Map<UUID, Short> dureesPrevues = parSeance.isEmpty() ? Map.of()
                : seanceRepository.findAllById(parSeance.keySet()).stream()
                        .filter(s -> s.getDureeMinutes() != null)
                        .collect(Collectors.toMap(Seance::getId, Seance::getDureeMinutes));
        return parSeance.entrySet().stream()
                .map(e -> agreger(e.getKey(), e.getValue(), moi, dureesPrevues.get(e.getKey())))
                .toList();
    }

    private SeanceRessenti agreger(UUID seanceId, List<RpeSeance> lignes, UUID moi, Short dureePrevue) {
        int dureeTotale = lignes.stream().mapToInt(r -> r.getDureeMinutes() == null ? 0 : r.getDureeMinutes()).sum();
        // Moyenne PONDÉRÉE par la durée : une séance de 90 min pèse plus qu'un 20 min.
        Double rpeMoyen = dureeTotale > 0
                ? lignes.stream().mapToDouble(r -> r.getRpe() * (r.getDureeMinutes() == null ? 0 : r.getDureeMinutes())).sum() / dureeTotale
                : (lignes.isEmpty() ? null : lignes.stream().mapToDouble(RpeSeance::getRpe).average().orElse(0));
        List<Integer> charges = lignes.stream().map(RpeSeance::getCharge).filter(java.util.Objects::nonNull).toList();
        Integer chargeMoyenne = charges.isEmpty() ? null
                : (int) Math.round(charges.stream().mapToInt(Integer::intValue).average().orElse(0));
        int nbGenes = (int) lignes.stream().filter(r -> r.getGeneZone() != null && !r.isGeneTraitee()).count();
        // Participation partielle : durée déclarée < durée planifiée de la séance.
        int nbPartiels = dureePrevue == null ? 0
                : (int) lignes.stream()
                        .filter(r -> r.getDureeMinutes() != null && r.getDureeMinutes() < dureePrevue)
                        .count();
        boolean moiFait = moi != null && lignes.stream().anyMatch(r -> moi.equals(r.getJoueurId()));
        return new SeanceRessenti(seanceId, lignes.size(),
                rpeMoyen == null ? null : Math.round(rpeMoyen * 10) / 10.0,
                chargeMoyenne, nbGenes, nbPartiels, moiFait);
    }

    private List<Anniversaire> anniversaires(LocalDate[] bornes) {
        UUID club;
        try {
            club = scopeResolver.clubActif();
        } catch (RuntimeException e) {
            return List.of();   // super-admin sans contexte de club : pas d'anniversaires à montrer
        }
        if (club == null) return List.of();

        // Fiches JOUEUR du club : tout ce qui n'y est pas et porte une fiche est du staff.
        Set<UUID> idsJoueurs = joueurRepository.findJoueursActifsByClub(club).stream()
                .map(Joueur::getId).collect(Collectors.toCollection(HashSet::new));

        // Un anniversaire est un événement RÉCURRENT : on compare (jour, mois) et non la date.
        Set<String> joursMois = new HashSet<>();
        for (LocalDate d = bornes[0]; !d.isAfter(bornes[1]); d = d.plusDays(1)) {
            joursMois.add(d.getMonthValue() + "-" + d.getDayOfMonth());
        }

        return joueurRepository.findByStatutNotAndClubId("inactif", club).stream()
                .filter(j -> j.getDateNaissance() != null)
                .filter(j -> joursMois.contains(
                        j.getDateNaissance().getMonthValue() + "-" + j.getDateNaissance().getDayOfMonth()))
                .map(j -> new Anniversaire(j.getId(), j.getNom(), j.getPrenom(),
                        j.getDateNaissance().getDayOfMonth(), j.getDateNaissance().getMonthValue(),
                        !idsJoueurs.contains(j.getId())))
                .sorted((a, b) -> Integer.compare(a.mois() * 100 + a.jour(), b.mois() * 100 + b.jour()))
                .toList();
    }

    // ──────────────────────────── Cadence & effectif ────────────────────────────

    /**
     * Jours ISO (1..7) où le ressenti est attendu. Source de vérité : la cadence du rappel
     * wellness de l'équipe (V74). Portée multi-équipes → UNION : un jour attendu par une seule
     * équipe reste un jour attendu à l'écran.
     */
    private Set<Integer> joursAttendus(Scope scope) {
        if (scope.all() || scope.none() || scope.equipeIds().isEmpty()) {
            return joursDe(JOURS_PAR_DEFAUT);
        }
        Set<Integer> union = new HashSet<>();
        for (UUID equipeId : scope.equipeIds()) {
            String csv = notifConfigRepository.findByEquipeId(equipeId)
                    .map(NotifConfigEquipe::getRappelWellnessJours)
                    .orElse(JOURS_PAR_DEFAUT);
            union.addAll(joursDe(csv));
        }
        return union.isEmpty() ? joursDe(JOURS_PAR_DEFAUT) : union;
    }

    private Set<Integer> joursDe(String csv) {
        if (csv == null || csv.isBlank()) return joursDe(JOURS_PAR_DEFAUT);
        return Arrays.stream(csv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> {
                    try { return Integer.parseInt(s); } catch (NumberFormatException e) { return -1; }
                })
                .filter(n -> n >= 1 && n <= 7)
                .collect(Collectors.toSet());
    }

    /** Effectif servant de dénominateur au taux de remplissage. */
    private int effectifDeReference(Scope scope) {
        if (scope.none()) return 0;
        if (!scope.equipeIds().isEmpty()) {
            return (int) joueurRepository.countByEquipeIdIn(scope.equipeIds());
        }
        try {
            UUID club = scopeResolver.clubActif();
            return club == null ? 0 : joueurRepository.findJoueursActifsByClub(club).size();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Fenêtre bornée et ordonnée — une plage inversée ou démesurée ne doit pas atteindre la base. */
    private LocalDate[] borner(LocalDate debut, LocalDate fin) {
        LocalDate d = debut != null ? debut : LocalDate.now();
        LocalDate f = fin != null ? fin : d;
        if (f.isBefore(d)) { LocalDate tmp = d; d = f; f = tmp; }
        if (d.plusDays(JOURS_MAX).isBefore(f)) f = d.plusDays(JOURS_MAX);
        return new LocalDate[]{d, f};
    }
}
