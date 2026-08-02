package com.remipreparateur.joueur.service;

import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.auth.repository.UtilisateurRepository;
import com.remipreparateur.entretien.repository.EntretienRepository;
import com.remipreparateur.joueur.dto.SuiviCoachDtos.*;
import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.joueur.entity.NoteJoueur;
import com.remipreparateur.joueur.entity.ObjectifJoueur;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.joueur.repository.NoteJoueurRepository;
import com.remipreparateur.joueur.repository.ObjectifJoueurRepository;
import com.remipreparateur.medical.blessure.entity.Blessure;
import com.remipreparateur.medical.blessure.repository.BlessureRepository;
import com.remipreparateur.shared.security.CurrentUserProvider;
import com.remipreparateur.shared.security.ScopeResolver;
import com.remipreparateur.shared.time.Horloge;
import com.remipreparateur.tactical.match.repository.MatchCompoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Relation entraîneur ↔ joueur (V98) : objectifs individuels, notes du staff et fil de vie.
 *
 * <p>Rattaché au module {@code suivi_individuel}, aux côtés des entretiens et des axes de travail.
 * Toutes les opérations vérifient la portée de la fiche avant de lire ou d'écrire.
 */
@Service
public class SuiviCoachService {

    private static final Set<String> STATUTS_OBJECTIF = Set.of("EN_COURS", "ATTEINT", "ABANDONNE");

    private final ObjectifJoueurRepository objectifRepository;
    private final NoteJoueurRepository noteRepository;
    private final JoueurRepository joueurRepository;
    private final BlessureRepository blessureRepository;
    private final EntretienRepository entretienRepository;
    private final MatchCompoRepository compoRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final CurrentUserProvider currentUser;
    private final ScopeResolver scopeResolver;
    private final Horloge horloge;

    public SuiviCoachService(ObjectifJoueurRepository objectifRepository,
                             NoteJoueurRepository noteRepository,
                             JoueurRepository joueurRepository,
                             BlessureRepository blessureRepository,
                             EntretienRepository entretienRepository,
                             MatchCompoRepository compoRepository,
                             UtilisateurRepository utilisateurRepository,
                             CurrentUserProvider currentUser,
                             ScopeResolver scopeResolver,
                             Horloge horloge) {
        this.objectifRepository = objectifRepository;
        this.noteRepository = noteRepository;
        this.joueurRepository = joueurRepository;
        this.blessureRepository = blessureRepository;
        this.entretienRepository = entretienRepository;
        this.compoRepository = compoRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.currentUser = currentUser;
        this.scopeResolver = scopeResolver;
        this.horloge = horloge;
    }

    // ── Objectifs individuels ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ObjectifResponse> objectifs(UUID joueurId) {
        verifierPortee(joueurId);
        LocalDate today = horloge.today();
        return objectifRepository.findByJoueurIdOrderByStatutAscEcheanceAsc(joueurId).stream()
                .map(o -> toObjectif(o, today))
                .toList();
    }

    @Transactional
    public ObjectifResponse creerObjectif(UUID joueurId, ObjectifRequest req) {
        verifierPortee(joueurId);
        ObjectifJoueur o = new ObjectifJoueur();
        o.setJoueurId(joueurId);
        o.setCreePar(currentUser.current().getId());
        appliquer(o, req);
        return toObjectif(objectifRepository.save(o), horloge.today());
    }

    @Transactional
    public ObjectifResponse modifierObjectif(UUID objectifId, ObjectifRequest req) {
        ObjectifJoueur o = objectifRepository.findById(objectifId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Objectif introuvable"));
        verifierPortee(o.getJoueurId());
        appliquer(o, req);
        return toObjectif(objectifRepository.save(o), horloge.today());
    }

    @Transactional
    public void supprimerObjectif(UUID objectifId) {
        ObjectifJoueur o = objectifRepository.findById(objectifId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Objectif introuvable"));
        verifierPortee(o.getJoueurId());
        objectifRepository.delete(o);
    }

    private void appliquer(ObjectifJoueur o, ObjectifRequest req) {
        o.setTitre(req.titre().trim());
        o.setDescription(req.description());
        o.setEcheance(req.echeance());
        // Un statut inconnu violerait la contrainte CHECK : on retombe sur EN_COURS plutôt que
        // de laisser remonter une erreur de base illisible côté écran.
        o.setStatut(req.statut() != null && STATUTS_OBJECTIF.contains(req.statut()) ? req.statut() : "EN_COURS");
        o.setUpdatedAt(LocalDateTime.now());
    }

    private ObjectifResponse toObjectif(ObjectifJoueur o, LocalDate today) {
        boolean enRetard = "EN_COURS".equals(o.getStatut())
                && o.getEcheance() != null && o.getEcheance().isBefore(today);
        return new ObjectifResponse(o.getId(), o.getTitre(), o.getDescription(), o.getEcheance(),
                o.getStatut(), enRetard, nomUtilisateur(o.getCreePar()),
                o.getCreatedAt() != null ? o.getCreatedAt().toLocalDate() : null);
    }

    // ── Notes du staff ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<NoteResponse> notes(UUID joueurId) {
        verifierPortee(joueurId);
        return noteRepository.findByJoueurIdOrderByDateNoteDescCreatedAtDesc(joueurId).stream()
                .map(n -> new NoteResponse(n.getId(), n.getTexte(), n.getDateNote(), nomUtilisateur(n.getAuteurId())))
                .toList();
    }

    @Transactional
    public NoteResponse creerNote(UUID joueurId, NoteRequest req) {
        verifierPortee(joueurId);
        NoteJoueur n = new NoteJoueur();
        n.setJoueurId(joueurId);
        n.setTexte(req.texte().trim());
        n.setDateNote(req.dateNote() != null ? req.dateNote() : horloge.today());
        n.setAuteurId(currentUser.current().getId());
        noteRepository.save(n);
        return new NoteResponse(n.getId(), n.getTexte(), n.getDateNote(), nomUtilisateur(n.getAuteurId()));
    }

    @Transactional
    public void supprimerNote(UUID noteId) {
        NoteJoueur n = noteRepository.findById(noteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Note introuvable"));
        verifierPortee(n.getJoueurId());
        noteRepository.delete(n);
    }

    // ── Fil de vie ──────────────────────────────────────────────────────────

    /**
     * Tout ce qui est arrivé au joueur, en une seule chronologie. Rien n'est stocké : on relit les
     * domaines qui écrivent déjà (médical, match, entretiens, objectifs, notes). Une table dédiée
     * aurait dupliqué un historique, donc créé deux versions de la vérité.
     */
    @Transactional(readOnly = true)
    public FilDeVie filDeVie(UUID joueurId, LocalDate depuis) {
        Joueur j = verifierPortee(joueurId);
        LocalDate fin = horloge.today();
        LocalDate debut = depuis != null ? depuis : fin.minusYears(1);
        List<EvenementVie> evts = new ArrayList<>();

        for (Blessure b : blessureRepository.findByJoueurIdOrderByDateBlessureDesc(joueurId)) {
            if (dansPeriode(b.getDateBlessure(), debut, fin)) {
                evts.add(new EvenementVie(b.getDateBlessure(), "BLESSURE",
                        libelleBlessure(b), b.getGravite() != null ? "Gravité : " + b.getGravite() : null, "bad"));
            }
            if (dansPeriode(b.getDateRetourEffectif(), debut, fin)) {
                evts.add(new EvenementVie(b.getDateRetourEffectif(), "RETOUR",
                        "Retour à l'effectif", libelleBlessure(b), "ok"));
            }
        }

        entretienRepository.findByJoueurIdOrderByDateEntretienDescCreatedAtDesc(joueurId).stream()
                .filter(e -> dansPeriode(e.getDateEntretien(), debut, fin))
                .forEach(e -> evts.add(new EvenementVie(e.getDateEntretien(), "ENTRETIEN",
                        "Entretien" + (e.getType() != null ? " — " + e.getType().toLowerCase() : ""),
                        nomUtilisateur(e.getMenePar()) != null ? "Mené par " + nomUtilisateur(e.getMenePar()) : null,
                        "neutre")));

        for (ObjectifJoueur o : objectifRepository.findByJoueurIdOrderByStatutAscEcheanceAsc(joueurId)) {
            LocalDate d = o.getCreatedAt() != null ? o.getCreatedAt().toLocalDate() : null;
            if (dansPeriode(d, debut, fin)) {
                evts.add(new EvenementVie(d, "OBJECTIF", "Objectif : " + o.getTitre(),
                        o.getEcheance() != null ? "Échéance " + o.getEcheance() : null,
                        "ATTEINT".equals(o.getStatut()) ? "ok" : "neutre"));
            }
        }

        noteRepository.findByJoueurIdOrderByDateNoteDescCreatedAtDesc(joueurId).stream()
                .filter(n -> dansPeriode(n.getDateNote(), debut, fin))
                .forEach(n -> evts.add(new EvenementVie(n.getDateNote(), "NOTE",
                        "Note du staff", n.getTexte(), "neutre")));

        ajouterMatchs(joueurId, debut, fin, evts);

        evts.sort(Comparator.comparing(EvenementVie::date, Comparator.nullsLast(Comparator.reverseOrder())));
        return new FilDeVie(j.getId(), debut, fin, evts);
    }

    /** Les rencontres du joueur : celles où il a joué, et celles où il est resté sur le banc. */
    private void ajouterMatchs(UUID joueurId, LocalDate debut, LocalDate fin, List<EvenementVie> evts) {
        UUID equipeId;
        try {
            equipeId = scopeResolver.equipeActiveUnique();
        } catch (ResponseStatusException e) {
            return;   // sans équipe active, le fil se limite aux domaines non liés au match
        }
        compoRepository.historiqueJoueur(joueurId, equipeId, debut).forEach(agg -> {
            var m = agg.getMatch();
            var c = agg.getCompo();
            if (!dansPeriode(m.getDateMatch(), debut, fin)) return;
            String titre = (m.isDomicile() ? "Match contre " : "Déplacement à ") + m.getAdversaire();
            StringBuilder detail = new StringBuilder(c.getStatut() == null ? "" : c.getStatut().toLowerCase());
            if (c.getButs() > 0) detail.append(" · ").append(c.getButs()).append(" but(s)");
            if (c.getPassesDecisives() > 0) detail.append(" · ").append(c.getPassesDecisives()).append(" passe(s) déc.");
            if (c.isCartonRouge()) detail.append(" · carton rouge");
            if (m.getScore() != null) detail.append(" · ").append(m.getScore());
            evts.add(new EvenementVie(m.getDateMatch(), "MATCH", titre, detail.toString(),
                    c.isCartonRouge() ? "warn" : "neutre"));
        });
    }

    private String libelleBlessure(Blessure b) {
        String zone = b.getZoneCorporelle() != null ? b.getZoneCorporelle().replace('_', ' ') : "zone non précisée";
        return (b.getTypeBlessure() != null ? b.getTypeBlessure().replace('_', ' ') : "Blessure") + " — " + zone;
    }

    private boolean dansPeriode(LocalDate d, LocalDate debut, LocalDate fin) {
        return d != null && !d.isBefore(debut) && !d.isAfter(fin);
    }

    // ── Commun ──────────────────────────────────────────────────────────────

    private Joueur verifierPortee(UUID joueurId) {
        Joueur j = joueurRepository.findById(joueurId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Joueur introuvable"));
        scopeResolver.verifieAccesPersonne(j.getId(), j.getClubId());
        return j;
    }

    private String nomUtilisateur(UUID id) {
        if (id == null) return null;
        return utilisateurRepository.findById(id)
                .map(this::nomComplet)
                .orElse(null);
    }

    private String nomComplet(Utilisateur u) {
        String prenom = u.getPrenom() != null ? u.getPrenom() : "";
        String nom = u.getNom() != null ? u.getNom() : "";
        String complet = (prenom + " " + nom).trim();
        return complet.isEmpty() ? null : complet;
    }
}
