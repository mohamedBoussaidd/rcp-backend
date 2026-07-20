package com.remipreparateur.performance.seance.service;

import com.remipreparateur.auth.entity.Role;
import com.remipreparateur.auth.rbac.PermissionResolver;
import com.remipreparateur.auth.repository.UtilisateurRepository;
import com.remipreparateur.club.entity.Equipe;
import com.remipreparateur.club.repository.EquipeRepository;
import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.medical.blessure.entity.Blessure;
import com.remipreparateur.medical.blessure.repository.BlessureRepository;
import com.remipreparateur.notification.entity.Priorite;
import com.remipreparateur.notification.entity.TypeNotification;
import com.remipreparateur.notification.service.NotificationDispatcher;
import com.remipreparateur.performance.seance.dto.SeanceDtos.*;
import com.remipreparateur.performance.seance.entity.Presence;
import com.remipreparateur.performance.seance.entity.Seance;
import com.remipreparateur.performance.seance.entity.TypeSeance;
import com.remipreparateur.performance.seance.repository.PresenceRepository;
import com.remipreparateur.performance.seance.repository.ReferentielDominanteRepository;
import com.remipreparateur.performance.seance.repository.ReferentielSousPrincipeRepository;
import com.remipreparateur.performance.seance.repository.SeanceRepository;
import com.remipreparateur.saison.service.AppartenanceService;
import com.remipreparateur.shared.security.CurrentUserProvider;
import com.remipreparateur.shared.security.ScopeResolver;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Fiche séance (résumé) et ses satellites : périodisation J±X (matchs du calendrier),
 * groupes auto (médical / RTP / effectif), partage au staff, et la version FILTRÉE pour
 * le joueur (jamais d'objectifs, de dominantes, de projet de jeu ni d'affectation staff).
 */
@Service
@RequiredArgsConstructor
public class SeanceFicheService {

    /** Fenêtre de recherche du match de référence pour le badge J±X. */
    private static final int FENETRE_PERIMATCH_JOURS = 10;

    private static final Set<String> CODES_MATCH = Set.of("MATCH", "MATCH_AMICAL");

    private final SeanceRepository seanceRepository;
    private final SeanceService seanceService;
    private final BlessureRepository blessureRepository;
    private final JoueurRepository joueurRepository;
    private final PresenceRepository presenceRepository;
    private final EquipeRepository equipeRepository;
    private final ReferentielDominanteRepository dominanteRepository;
    private final ReferentielSousPrincipeRepository sousPrincipeRepository;
    private final NotificationDispatcher dispatcher;
    private final CurrentUserProvider currentUser;
    private final AppartenanceService appartenance;
    private final ScopeResolver scopeResolver;
    private final PermissionResolver permissionResolver;
    private final UtilisateurRepository utilisateurRepository;

    // ══════════ Périodisation J±X ══════════

    /** Match de référence le plus proche de {@code date} pour l'équipe (fenêtre ±10 j), ou champs null. */
    public PerimatchDto perimatch(UUID equipeId, LocalDate date) {
        scopeResolver.verifieAcces(equipeId);
        List<Seance> matchs = seanceRepository
                .findByDateBetweenAndEquipeIdInOrderByDateAscHeureDebutAsc(
                        date.minusDays(FENETRE_PERIMATCH_JOURS), date.plusDays(FENETRE_PERIMATCH_JOURS),
                        List.of(equipeId))
                .stream()
                .filter(s -> {
                    TypeSeance t = (TypeSeance) Hibernate.unproxy(s.getTypeSeance());
                    return t != null && CODES_MATCH.contains(t.getCode());
                })
                .toList();

        Seance dernier = matchs.stream()
                .filter(m -> !m.getDate().isAfter(date))
                .max(Comparator.comparing(Seance::getDate))
                .orElse(null);
        Seance prochain = matchs.stream()
                .filter(m -> m.getDate().isAfter(date))
                .min(Comparator.comparing(Seance::getDate))
                .orElse(null);

        // Référence = le match le plus proche ; à égalité, le match À VENIR (la prépa vise lui).
        Seance ref;
        boolean versProchain;
        if (dernier == null && prochain == null) {
            return new PerimatchDto(null, null, null, null, null, false);
        } else if (prochain == null) {
            ref = dernier; versProchain = false;
        } else if (dernier == null) {
            ref = prochain; versProchain = true;
        } else {
            long depuis = dernier.getDate().until(date).getDays();
            long avant = date.until(prochain.getDate()).getDays();
            versProchain = avant <= depuis;
            ref = versProchain ? prochain : dernier;
        }

        int j = versProchain
                ? -(int) date.until(ref.getDate()).getDays()
                : (int) ref.getDate().until(date).getDays();
        String adversaire = ref.getAdversaire();
        String score = ref.getScoreMatch();
        String quand = versProchain
                ? "avant " + (adversaire != null ? adversaire : "le match")
                : "après " + (adversaire != null ? adversaire : "le match");
        String libelle = "J" + (j >= 0 ? "+" + j : j) + " · " + quand
                + (score != null && !score.isBlank() ? " (" + score + ")" : "");
        return new PerimatchDto(j, libelle, ref.getDate(), adversaire, score, versProchain);
    }

    // ══════════ Staff du club (sélecteur d'affectation des blocs) ══════════

    /** Comptes staff du club actif (id + nom + rôle), pour affecter les blocs de séance. */
    public List<StaffRef> staffDuClub() {
        UUID clubId = permissionResolver.clubActif(currentUser.current());
        if (clubId == null) return List.of();
        // Cache local des libellés d'équipe : le club a peu d'équipes, mais beaucoup de comptes.
        Map<UUID, String> equipes = new HashMap<>();
        return utilisateurRepository.findByClubIdAndRoleIn(clubId,
                        List.of(Role.PRESIDENT, Role.ENTRAINEUR, Role.PREPARATEUR, Role.MEDICAL, Role.ADMINISTRATIF))
                .stream()
                .map(u -> StaffRef.de(u.getId(), u.getPrenom(), u.getNom(), u.getRole(),
                        libelleEquipe(u.getEquipeId(), equipes)))
                .sorted(Comparator.comparing(StaffRef::nom)
                        .thenComparing(s -> s.equipe() == null ? "" : s.equipe()))
                .toList();
    }

    /** Nom de l'équipe de rattachement (null pour un compte club seul), mémoïsé par appel. */
    private String libelleEquipe(UUID equipeId, Map<UUID, String> cache) {
        if (equipeId == null) return null;
        return cache.computeIfAbsent(equipeId,
                id -> equipeRepository.findById(id).map(Equipe::getNom).orElse(null));
    }

    // ══════════ Groupes auto (jamais stockés) ══════════

    /** Blessés / réathlétisation / reste de l'effectif pour une équipe (aperçu et fiche). */
    public GroupesAutoDto groupesAuto(UUID equipeId) {
        scopeResolver.verifieAcces(equipeId);
        List<Blessure> actives = blessureRepository.findByEquipeIdAndDateRetourEffectifIsNull(equipeId);
        Set<UUID> blessesIds = actives.stream()
                .filter(b -> !"EN_REPRISE".equals(b.getStatut()))
                .map(Blessure::getJoueurId).collect(Collectors.toSet());
        Set<UUID> reathIds = actives.stream()
                .filter(b -> "EN_REPRISE".equals(b.getStatut()))
                .map(Blessure::getJoueurId).collect(Collectors.toSet());
        // Un joueur à la fois indisponible ET en reprise (2 blessures) compte comme blessé.
        reathIds.removeAll(blessesIds);

        List<Joueur> effectif = joueurRepository.findByEquipeIdIn(List.of(equipeId)).stream()
                .filter(j -> !"inactif".equalsIgnoreCase(j.getStatut()))
                .toList();
        Map<UUID, Joueur> parId = effectif.stream().collect(Collectors.toMap(Joueur::getId, Function.identity()));

        List<JoueurRef> blesses = refs(blessesIds, parId);
        List<JoueurRef> reath = refs(reathIds, parId);
        List<JoueurRef> disponibles = effectif.stream()
                .filter(j -> !blessesIds.contains(j.getId()) && !reathIds.contains(j.getId()))
                .map(j -> new JoueurRef(j.getId(), j.getNom(), j.getPrenom()))
                .toList();
        return new GroupesAutoDto(blesses, reath, disponibles);
    }

    private List<JoueurRef> refs(Set<UUID> ids, Map<UUID, Joueur> parId) {
        return ids.stream()
                .map(parId::get)
                .filter(j -> j != null)
                .sorted(Comparator.comparing(Joueur::getNom))
                .map(j -> new JoueurRef(j.getId(), j.getNom(), j.getPrenom()))
                .toList();
    }

    // ══════════ Fiche séance (staff) ══════════

    @Transactional(readOnly = true)
    public ResumeSeance resume(UUID seanceId) {
        Seance s = seanceRepository.findById(seanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Séance introuvable"));
        scopeResolver.verifieAcces(s.getEquipeId());
        TypeSeance type = (TypeSeance) Hibernate.unproxy(s.getTypeSeance());

        ContenuSeance contenu = seanceService.getContenu(seanceId);
        Map<UUID, List<ExerciceLigne>> lignesParBloc = contenu.exercices().stream()
                .filter(l -> l.blocId() != null)
                .collect(Collectors.groupingBy(ExerciceLigne::blocId));
        List<BlocResume> blocs = contenu.blocs().stream()
                .map(b -> new BlocResume(b, lignesParBloc.getOrDefault(b.id(), List.of())))
                .toList();
        List<ExerciceLigne> sansBloc = contenu.exercices().stream()
                .filter(l -> l.blocId() == null)
                .toList();

        List<RefItem> dominantes = dominanteRepository.findAllById(contenu.dominanteIds()).stream()
                .map(d -> new RefItem(d.getCode(), d.getLibelle(), d.getFamille()))
                .toList();
        List<RefItem> sousPrincipes = sousPrincipeRepository.findAllById(contenu.sousPrincipeIds()).stream()
                .map(p -> new RefItem(p.getCode(), p.getLibelle(), p.getPhase()))
                .toList();

        List<JoueurRef> absents = presenceRepository.findBySeanceId(seanceId).stream()
                .filter(p -> p.getStatut() == Presence.StatutPresence.ABSENT
                        || p.getStatut() == Presence.StatutPresence.EXCUSE)
                .map(p -> joueurRepository.findById(p.getJoueurId())
                        .map(j -> new JoueurRef(j.getId(), j.getNom(), j.getPrenom()))
                        .orElse(null))
                .filter(r -> r != null)
                .toList();

        PerimatchDto perimatch = s.getEquipeId() != null && s.getDate() != null
                ? perimatch(s.getEquipeId(), s.getDate())
                : new PerimatchDto(null, null, null, null, null, false);

        String equipeNom = s.getEquipeId() != null
                ? equipeRepository.findById(s.getEquipeId()).map(Equipe::getNom).orElse(null)
                : null;

        return new ResumeSeance(
                s.getId(), s.getTitre(), s.getStatut(), s.getDate(), s.getHeureDebut(),
                s.getDureeMinutes(), s.getDureeEffectiveMinutes(), s.getTerrain(), s.getResponsable(),
                type != null ? type.getCode() : null, type != null ? type.getLibelle() : null, equipeNom,
                perimatch, dominantes, sousPrincipes,
                new ObjectifsPedagogiques(s.getObjTactiqueOrg(), s.getObjTactiqueFonc(),
                        s.getObjMental(), s.getObjTechnique(), s.getObjAthletique()),
                s.getObjectifDistanceM(), s.getObjectifDistanceHauteIntensiteM(), s.getObjectifIntensite(),
                blocs, sansBloc, contenu.groupes(),
                s.getEquipeId() != null ? groupesAuto(s.getEquipeId()) : new GroupesAutoDto(List.of(), List.of(), List.of()),
                absents);
    }

    // ══════════ Partage au staff ══════════

    /** Notifie (in-app + push) le staff de l'équipe de la séance, avec lien vers la fiche. */
    public int partagerAuStaff(UUID seanceId) {
        Seance s = seanceRepository.findById(seanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Séance introuvable"));
        scopeResolver.verifieAcces(s.getEquipeId());
        if (s.getEquipeId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Séance sans équipe : partage impossible");
        }
        String jour = s.getDate() != null
                ? s.getDate().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.FRENCH)
                        + " " + s.getDate().format(DateTimeFormatter.ofPattern("dd/MM"))
                : "";
        String titre = "Fiche séance partagée" + (jour.isBlank() ? "" : " — " + jour);
        String corps = (s.getTitre() != null && !s.getTitre().isBlank() ? s.getTitre() : "Séance")
                + (s.getHeureDebut() != null ? " · " + s.getHeureDebut() : "");
        return dispatcher.versStaffRoles(s.getEquipeId(),
                List.of(Role.PRESIDENT, Role.ENTRAINEUR, Role.PREPARATEUR, Role.MEDICAL, Role.ADMINISTRATIF),
                TypeNotification.SEANCE_PARTAGEE, titre, corps,
                "/seances/" + seanceId + "/fiche", Priorite.NORMALE,
                currentUser.current().getId());
    }

    // ══════════ Fiche joueur (filtrée serveur) ══════════

    /** Version joueur : horaire, lieu, déroulé (blocs + schémas) et SES groupes uniquement. */
    @Transactional(readOnly = true)
    public FicheSeanceJoueur ficheJoueur(UUID seanceId, UUID joueurId) {
        Seance s = seanceRepository.findById(seanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Séance introuvable"));
        if (s.getEquipeId() == null || !appartenance.equipesDe(joueurId).contains(s.getEquipeId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Séance introuvable");
        }
        TypeSeance type = (TypeSeance) Hibernate.unproxy(s.getTypeSeance());
        ContenuSeance contenu = seanceService.getContenuSansScope(seanceId);

        Map<UUID, List<ExerciceLigne>> lignesParBloc = contenu.exercices().stream()
                .filter(l -> l.blocId() != null)
                .collect(Collectors.groupingBy(ExerciceLigne::blocId));
        Map<UUID, String> libelleBloc = contenu.blocs().stream()
                .collect(Collectors.toMap(BlocDto::id, BlocDto::libelle));

        List<BlocJoueur> blocs = contenu.blocs().stream()
                .map(b -> new BlocJoueur(b.libelle(), b.sequencage(), b.dureeMinutes(), b.zoneTerrain(),
                        lignesParBloc.getOrDefault(b.id(), List.of()).stream()
                                .map(l -> new ExerciceJoueur(l.nom(), l.dureeMinutes(), l.schemaJson()))
                                .toList()))
                .toList();
        List<ExerciceJoueur> sansBloc = contenu.exercices().stream()
                .filter(l -> l.blocId() == null)
                .map(l -> new ExerciceJoueur(l.nom(), l.dureeMinutes(), l.schemaJson()))
                .toList();

        List<MonGroupe> mesGroupes = new ArrayList<>();
        for (GroupeDto g : contenu.groupes()) {
            boolean dedans = g.joueurs().stream().anyMatch(j -> j.id().equals(joueurId));
            if (!dedans) continue;
            List<String> coequipiers = g.joueurs().stream()
                    .filter(j -> !j.id().equals(joueurId))
                    .map(j -> (j.prenom() != null ? j.prenom() + " " : "") + j.nom())
                    .toList();
            mesGroupes.add(new MonGroupe(g.libelle(), g.couleur(),
                    g.blocId() != null ? libelleBloc.get(g.blocId()) : null, coequipiers));
        }

        return new FicheSeanceJoueur(s.getId(), s.getTitre(), s.getDate(), s.getHeureDebut(),
                s.getDureeMinutes(), s.getTerrain(), type != null ? type.getLibelle() : null,
                blocs, sansBloc, mesGroupes);
    }
}
