package com.remipreparateur.performance.seance.service;

import com.remipreparateur.performance.seance.dto.PresenceDtos.*;
import com.remipreparateur.club.entity.Equipe;
import com.remipreparateur.club.repository.EquipeRepository;
import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.notification.service.NotificationProducer;
import com.remipreparateur.performance.seance.entity.Presence;
import com.remipreparateur.performance.seance.entity.Presence.StatutPresence;
import com.remipreparateur.performance.seance.entity.Seance;
import com.remipreparateur.performance.seance.repository.PresenceRepository;
import com.remipreparateur.performance.seance.repository.SeanceRepository;
import com.remipreparateur.saison.entity.EffectifSaison;
import com.remipreparateur.saison.entity.Saison;
import com.remipreparateur.saison.repository.EffectifSaisonRepository;
import com.remipreparateur.saison.repository.SaisonRepository;
import com.remipreparateur.shared.security.ScopeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PresenceService {

    private final PresenceRepository presenceRepository;
    private final SeanceRepository seanceRepository;
    private final JoueurRepository joueurRepository;
    private final EquipeRepository equipeRepository;
    private final SaisonRepository saisonRepository;
    private final EffectifSaisonRepository effectifRepository;
    private final NotificationProducer notificationProducer;
    private final ScopeResolver scopeResolver;

    /**
     * Retourne la feuille d'appel d'une séance : l'effectif de la saison active de l'équipe, chaque
     * joueur étant <b>PRÉSENT par défaut</b> ; seules les déviations (absent/excusé/retard) sont
     * stockées. Les blessés sont dérivés du statut médical du joueur et signalés à part.
     */
    @Transactional(readOnly = true)
    public FeuillePresence getFeuille(UUID seanceId) {
        Seance seance = seanceRepository.findById(seanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Séance introuvable"));
        scopeResolver.verifieAcces(seance.getEquipeId());

        List<Joueur> joueurs = effectifDeSeance(seance);

        Map<UUID, Presence> existantes = presenceRepository.findBySeanceId(seanceId)
                .stream().collect(Collectors.toMap(Presence::getJoueurId, Function.identity()));

        List<LignePresence> lignes = joueurs.stream()
                .sorted((a, b) -> nomDe(a).compareToIgnoreCase(nomDe(b)))
                .map(j -> ligne(j, existantes.get(j.getId())))
                .collect(Collectors.toList());

        return new FeuillePresence(seanceId, lignes);
    }

    /** Sauvegarde la feuille complète (upsert : crée ou met à jour chaque ligne), source = STAFF. */
    @Transactional
    public FeuillePresence saveFeuille(UUID seanceId, SaveFeuillePresence req) {
        Seance seance = seanceRepository.findById(seanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Séance introuvable"));
        scopeResolver.verifieAcces(seance.getEquipeId());

        for (SaveFeuillePresence.SaveLigne ligne : req.lignes()) {
            upsert(seanceId, ligne.joueurId(), ligne.statut(), ligne.note(), "STAFF");
        }
        return getFeuille(seanceId);
    }

    /** Sauvegarde/met à jour la présence d'un seul joueur (appel staff). */
    @Transactional
    public LignePresence saveUne(UUID seanceId, UUID joueurId, SavePresence req) {
        Seance seance = seanceRepository.findById(seanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Séance introuvable"));
        scopeResolver.verifieAcces(seance.getEquipeId());

        Presence p = upsert(seanceId, joueurId, req.statut(), req.note(), "STAFF");
        return ligne(joueurRequis(joueurId), p);
    }

    /**
     * Auto-déclaration de présence par le joueur depuis la PWA (source = JOUEUR). Le contrôle de
     * périmètre (la séance appartient bien à l'équipe du joueur) est fait par l'appelant
     * (EspaceJoueurController). Une absence/retard déclaré(e) notifie le staff.
     */
    @Transactional
    public LignePresence declarerParJoueur(UUID seanceId, UUID joueurId, DeclarationPresence req) {
        Seance seance = seanceRepository.findById(seanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Séance introuvable"));

        StatutPresence statut = req.statut() != null ? req.statut() : StatutPresence.PRESENT;
        Presence p = upsert(seanceId, joueurId, statut, req.commentaire(), "JOUEUR");
        Joueur joueur = joueurRequis(joueurId);

        if (statut != StatutPresence.PRESENT) {
            String nom = (joueur.getPrenom() != null ? joueur.getPrenom() + " " : "") + nomDe(joueur);
            String quand = seance.getDate() != null ? " (séance du " + seance.getDate() + ")" : "";
            notificationProducer.absenceDeclaree(seance.getEquipeId(), joueurId, nom.trim(),
                    libelleStatut(statut), quand);
        }
        return ligne(joueur, p);
    }

    /** Résumés d'appel pour plusieurs séances (dashboard) ; les séances hors périmètre sont ignorées. */
    @Transactional(readOnly = true)
    public List<ResumeAppel> resumes(List<UUID> seanceIds) {
        List<ResumeAppel> out = new ArrayList<>();
        if (seanceIds == null) return out;
        for (UUID id : seanceIds) {
            Seance seance = seanceRepository.findById(id).orElse(null);
            if (seance == null) continue;
            try { scopeResolver.verifieAcces(seance.getEquipeId()); }
            catch (ResponseStatusException e) { continue; }   // hors périmètre → on saute

            Map<UUID, Presence> ex = presenceRepository.findBySeanceId(id)
                    .stream().collect(Collectors.toMap(Presence::getJoueurId, Function.identity()));
            int effectif = 0, blesses = 0, absents = 0, excuses = 0, retards = 0;
            for (Joueur j : effectifDeSeance(seance)) {
                effectif++;
                if ("blesse".equalsIgnoreCase(j.getStatut())) { blesses++; continue; }
                Presence p = ex.get(j.getId());
                StatutPresence st = p != null ? p.getStatut() : StatutPresence.PRESENT;
                switch (st) {
                    case ABSENT -> absents++;
                    case EXCUSE -> excuses++;
                    case RETARD -> retards++;
                    default -> {}
                }
            }
            int presents = Math.max(0, effectif - blesses - absents - excuses - retards);
            int dispo = Math.max(0, effectif - blesses - absents - excuses);
            out.add(new ResumeAppel(id, effectif, presents, blesses, absents, excuses, retards, dispo));
        }
        return out;
    }

    /** Déclarations déjà saisies par le joueur (pour pré-remplir la PWA). */
    @Transactional(readOnly = true)
    public List<MaDeclaration> mesDeclarations(UUID joueurId) {
        return presenceRepository.findByJoueurId(joueurId).stream()
                .map(p -> new MaDeclaration(p.getSeanceId(), p.getStatut(), p.getNote()))
                .toList();
    }

    /**
     * Bilan d'assiduité d'un joueur sur la <b>saison EN_COURS</b> (entraînements uniquement, hors
     * matchs). Repli sur 12 mois glissants si aucune saison. Une déclaration n'est comptée que si sa
     * séance tombe dans la fenêtre (pas de cumul inter-saisons).
     */
    @Transactional(readOnly = true)
    public AssiduiteJoueur assiduite(UUID joueurId) {
        Joueur joueur = joueurRequis(joueurId);
        scopeResolver.verifieAcces(joueur.getEquipeId());
        return assiduiteSurFenetre(joueur, resoudreFenetre(joueur.getEquipeId(), null, null, null));
    }

    /**
     * Historique d'assiduité d'un joueur sur une fenêtre <b>paramétrable</b> (page dédiée Présence) :
     * saison explicite ou période libre {@code du}/{@code au}. Mêmes règles de comptage que
     * {@link #assiduite(UUID)}.
     */
    @Transactional(readOnly = true)
    public AssiduiteJoueur historiqueJoueur(UUID joueurId, UUID saisonId, LocalDate du, LocalDate au) {
        Joueur joueur = joueurRequis(joueurId);
        scopeResolver.verifieAcces(joueur.getEquipeId());
        return assiduiteSurFenetre(joueur, resoudreFenetre(joueur.getEquipeId(), saisonId, du, au));
    }

    /** Cœur du calcul d'assiduité d'un joueur sur une fenêtre déjà résolue. */
    private AssiduiteJoueur assiduiteSurFenetre(Joueur joueur, Fenetre f) {
        UUID joueurId = joueur.getId();
        UUID equipeId = joueur.getEquipeId();
        LocalDate today = LocalDate.now();

        Map<UUID, Seance> parId = new HashMap<>();
        if (equipeId != null) {
            for (Seance s : seanceRepository.findByDateBetweenAndEquipeIdInOrderByDateAscHeureDebutAsc(f.debut(), f.fin(), List.of(equipeId))) {
                if (estEntrainementComptable(s)) parId.put(s.getId(), s);
            }
        }
        List<Presence> rows = presenceRepository.findByJoueurId(joueurId);   // comptées seulement si la séance est dans la fenêtre

        int absents = 0, excuses = 0, retards = 0, recents = 0;
        LocalDate recentDepuis = today.minusDays(14);
        List<EvenementAssiduite> hist = new ArrayList<>();
        for (Presence p : rows) {
            Seance s = parId.get(p.getSeanceId());
            if (s == null || p.getStatut() == StatutPresence.PRESENT) continue;
            switch (p.getStatut()) {
                case ABSENT -> absents++;
                case EXCUSE -> excuses++;
                case RETARD -> retards++;
                default -> {}
            }
            LocalDate d = s.getDate();
            if (d != null && !d.isBefore(recentDepuis)) recents++;
            hist.add(new EvenementAssiduite(p.getSeanceId(), d, titreSeance(s),
                    p.getStatut(), p.getNote(), p.getSource()));
        }
        hist.sort(Comparator.comparing(EvenementAssiduite::date, Comparator.nullsLast(Comparator.naturalOrder())).reversed());

        int nbSeances = parId.size();
        int presents = Math.max(0, nbSeances - absents - excuses - retards);
        int taux = nbSeances > 0 ? Math.round(presents * 100f / nbSeances) : 100;

        return new AssiduiteJoueur(joueurId, f.saisonId(), f.saisonLibelle(),
                nbSeances, presents, absents, excuses, retards, taux, recents, hist);
    }

    /** Assiduité (résumé léger) de tous les joueurs du périmètre, regroupée par équipe pour rester efficace. */
    @Transactional(readOnly = true)
    public List<AssiduiteResume> assiduiteEquipe() {
        com.remipreparateur.shared.security.Scope scope = scopeResolver.resolve();
        if (scope.none()) return List.of();
        List<Joueur> joueurs = scope.all()
                ? joueurRepository.findAll()
                : joueurRepository.findByEquipeIdIn(scope.equipeIds());

        LocalDate today = LocalDate.now();
        LocalDate recentDepuis = today.minusDays(14);
        List<AssiduiteResume> out = new ArrayList<>();

        Map<UUID, List<Joueur>> parEquipe = joueurs.stream()
                .filter(j -> j.getEquipeId() != null)
                .collect(Collectors.groupingBy(Joueur::getEquipeId));

        for (Map.Entry<UUID, List<Joueur>> e : parEquipe.entrySet()) {
            UUID equipeId = e.getKey();
            Saison saison = saisonActive(equipeId);
            LocalDate debut = saison != null ? saison.getDateDebut() : today.minusMonths(12);
            LocalDate fin   = saison != null ? saison.getDateFin()   : today;

            // Entraînements de l'équipe sur la SAISON EN COURS uniquement.
            Map<UUID, LocalDate> fenetre = new HashMap<>();
            for (Seance s : seanceRepository.findByDateBetweenAndEquipeIdInOrderByDateAscHeureDebutAsc(debut, fin, List.of(equipeId))) {
                if (estEntrainementComptable(s)) fenetre.put(s.getId(), s.getDate());
            }
            int nbSeances = fenetre.size();

            for (Joueur j : e.getValue()) {
                int absents = 0, excuses = 0, retards = 0, recents = 0;
                for (Presence p : presenceRepository.findByJoueurId(j.getId())) {
                    LocalDate d = fenetre.get(p.getSeanceId());
                    if (d == null || p.getStatut() == StatutPresence.PRESENT) continue;
                    switch (p.getStatut()) {
                        case ABSENT -> absents++;
                        case EXCUSE -> excuses++;
                        case RETARD -> retards++;
                        default -> {}
                    }
                    if (!d.isBefore(recentDepuis)) recents++;
                }
                int presents = Math.max(0, nbSeances - absents - excuses - retards);
                int taux = nbSeances > 0 ? Math.round(presents * 100f / nbSeances) : 100;
                out.add(new AssiduiteResume(j.getId(), taux, absents, retards, excuses, recents));
            }
        }
        return out;
    }

    /**
     * Historique de présence en mode <b>Équipe</b> (page dédiée) : une ligne par entraînement
     * comptabilisé de la fenêtre (saison explicite, période libre, sinon saison EN_COURS / repli 12
     * mois), pour toutes les équipes du périmètre, du plus récent au plus ancien.
     */
    @Transactional(readOnly = true)
    public HistoriqueEquipe historiqueEquipe(UUID saisonId, LocalDate du, LocalDate au) {
        com.remipreparateur.shared.security.Scope scope = scopeResolver.resolve();
        if (scope.none()) return new HistoriqueEquipe(null, null, null, null, List.of());

        List<UUID> equipeIds = scope.all() ? null : scope.equipeIds();
        UUID equipePourSaison = (equipeIds != null && !equipeIds.isEmpty()) ? equipeIds.get(0) : null;
        Fenetre f = resoudreFenetre(equipePourSaison, saisonId, du, au);

        List<Seance> seances = equipeIds != null
                ? seanceRepository.findByDateBetweenAndEquipeIdInOrderByDateAscHeureDebutAsc(f.debut(), f.fin(), equipeIds)
                : seanceRepository.findByDateBetweenOrderByDateAscHeureDebutAsc(f.debut(), f.fin());

        List<LigneHistoriqueSeance> lignes = new ArrayList<>();
        for (Seance s : seances) {
            if (!estEntrainementComptable(s)) continue;
            Map<UUID, Presence> ex = presenceRepository.findBySeanceId(s.getId())
                    .stream().collect(Collectors.toMap(Presence::getJoueurId, Function.identity()));
            int effectif = 0, blesses = 0, absents = 0, excuses = 0, retards = 0;
            for (Joueur j : effectifDeSeance(s)) {
                effectif++;
                if ("blesse".equalsIgnoreCase(j.getStatut())) { blesses++; continue; }
                Presence p = ex.get(j.getId());
                StatutPresence st = p != null ? p.getStatut() : StatutPresence.PRESENT;
                switch (st) {
                    case ABSENT -> absents++;
                    case EXCUSE -> excuses++;
                    case RETARD -> retards++;
                    default -> {}
                }
            }
            int presents = Math.max(0, effectif - blesses - absents - excuses - retards);
            int dispo = Math.max(0, effectif - blesses - absents - excuses);
            int declaresJoueur = (int) ex.values().stream()
                    .filter(p -> "JOUEUR".equalsIgnoreCase(p.getSource())).count();
            int denom = effectif - blesses;
            int taux = denom > 0 ? Math.round(presents * 100f / denom) : 100;
            lignes.add(new LigneHistoriqueSeance(s.getId(), s.getDate(), titreSeance(s), typeLibelle(s),
                    effectif, presents, blesses, absents, excuses, retards, dispo, declaresJoueur, taux));
        }
        lignes.sort(Comparator.comparing(LigneHistoriqueSeance::date, Comparator.nullsLast(Comparator.naturalOrder())).reversed());

        return new HistoriqueEquipe(f.saisonId(), f.saisonLibelle(), f.debut(), f.fin(), lignes);
    }

    // ──────────────────────────── Helpers ────────────────────────────

    /** Fenêtre temporelle résolue pour l'historique : bornes + saison d'origine (si applicable). */
    private record Fenetre(LocalDate debut, LocalDate fin, UUID saisonId, String saisonLibelle) {}

    /**
     * Résout la fenêtre de calcul : période libre {@code du}/{@code au} prioritaire ; sinon saison
     * explicite {@code saisonId} ; sinon saison EN_COURS de l'équipe ; sinon repli 12 mois glissants.
     * Les bornes ne sont <b>pas cappées à aujourd'hui</b> : les séances futures de la saison portent
     * les auto-déclarations PWA et doivent rester comptabilisées.
     */
    private Fenetre resoudreFenetre(UUID equipeId, UUID saisonId, LocalDate du, LocalDate au) {
        LocalDate today = LocalDate.now();
        if (du != null || au != null) {
            LocalDate debut = du != null ? du : today.minusMonths(12);
            LocalDate fin   = au != null ? au : today;
            return new Fenetre(debut, fin, null, null);
        }
        Saison saison = saisonId != null
                ? saisonRepository.findById(saisonId).orElse(null)
                : saisonActive(equipeId);
        if (saison != null) {
            return new Fenetre(saison.getDateDebut(), saison.getDateFin(), saison.getId(), saison.getLibelle());
        }
        return new Fenetre(today.minusMonths(12), today, null, null);
    }

    /** Libellé du type de séance (chargé via EntityGraph sur les requêtes par fenêtre), ou null. */
    private static String typeLibelle(Seance s) {
        return s.getTypeSeance() != null ? s.getTypeSeance().getLibelle() : null;
    }

    /** Upsert d'une ligne de présence (statut + note + source). */
    private Presence upsert(UUID seanceId, UUID joueurId, StatutPresence statut, String note, String source) {
        Presence p = presenceRepository.findBySeanceIdAndJoueurId(seanceId, joueurId)
                .orElseGet(() -> {
                    Presence n = new Presence();
                    n.setSeanceId(seanceId);
                    n.setJoueurId(joueurId);
                    return n;
                });
        p.setStatut(statut != null ? statut : StatutPresence.PRESENT);
        p.setNote(note);
        p.setSource(source);
        return presenceRepository.save(p);
    }

    /** Construit une ligne d'appel : PRÉSENT par défaut si pas de saisie, blessé dérivé du médical. */
    private LignePresence ligne(Joueur j, Presence p) {
        boolean blesse = "blesse".equalsIgnoreCase(j.getStatut());
        return new LignePresence(
                j.getId(), j.getPrenom(), j.getNom(), j.getPostePrincipal(),
                p != null ? p.getStatut() : StatutPresence.PRESENT,
                p != null ? p.getNote() : null,
                blesse,
                p != null ? p.getSource() : null);
    }

    /** Effectif de la séance : saison active de l'équipe ; repli sur l'effectif global de l'équipe. */
    private List<Joueur> effectifDeSeance(Seance seance) {
        UUID equipeId = seance.getEquipeId();
        if (equipeId == null) return List.of();
        Saison saison = saisonActive(equipeId);
        if (saison != null) {
            List<UUID> ids = effectifRepository.findBySaisonIdAndEquipeId(saison.getId(), equipeId)
                    .stream().map(EffectifSaison::getJoueurId).toList();
            if (!ids.isEmpty()) return joueurRepository.findAllById(ids);
        }
        return joueurRepository.findByEquipeIdIn(List.of(equipeId));   // repli (pré-pivot / pas de saison)
    }

    /** Saison EN_COURS du club de l'équipe, ou null. */
    private Saison saisonActive(UUID equipeId) {
        if (equipeId == null) return null;
        Equipe equipe = equipeRepository.findById(equipeId).orElse(null);
        if (equipe == null) return null;
        return saisonRepository.findFirstByClubIdAndStatut(equipe.getClubId(), "EN_COURS").orElse(null);
    }

    private Joueur joueurRequis(UUID joueurId) {
        return joueurRepository.findById(joueurId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Joueur introuvable"));
    }

    private static String nomDe(Joueur j) { return j.getNom() != null ? j.getNom() : ""; }

    /** Séance comptabilisée pour l'assiduité : un entraînement (pas un match) non annulé. */
    private static boolean estEntrainementComptable(Seance s) {
        return (s.getAdversaire() == null || s.getAdversaire().isBlank()) && !"ANNULEE".equals(s.getStatut());
    }

    private static String titreSeance(Seance s) {
        if (s == null) return "Séance";
        if (s.getTitre() != null && !s.getTitre().isBlank()) return s.getTitre();
        return "Séance";
    }

    private static String libelleStatut(StatutPresence s) {
        return switch (s) {
            case ABSENT  -> "absent";
            case EXCUSE  -> "excusé";
            case RETARD  -> "en retard";
            case PRESENT -> "présent";
        };
    }
}
