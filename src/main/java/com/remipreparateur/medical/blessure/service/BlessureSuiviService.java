package com.remipreparateur.medical.blessure.service;

import com.remipreparateur.medical.blessure.dto.BlessureSuiviDtos.EtapeResponse;
import com.remipreparateur.medical.blessure.dto.BlessureSuiviDtos.NoteRequest;
import com.remipreparateur.medical.blessure.dto.BlessureSuiviDtos.NoteResponse;
import com.remipreparateur.medical.blessure.entity.Blessure;
import com.remipreparateur.medical.blessure.entity.BlessureNote;
import com.remipreparateur.medical.rtp.entity.RtpEtape;
import com.remipreparateur.medical.blessure.repository.BlessureNoteRepository;
import com.remipreparateur.medical.blessure.repository.BlessureRepository;
import com.remipreparateur.medical.rtp.repository.RtpEtapeRepository;
import com.remipreparateur.shared.security.CurrentUserProvider;
import com.remipreparateur.shared.security.ScopeResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Suivi de blessure : journal d'évolution (notes) + protocole de retour au jeu (RTP).
 * Accès calqué sur les blessures : lecture staff (scope équipe), écriture MEDICAL
 * (cf. SecurityConfig sur /api/blessures/**).
 */
@Service
public class BlessureSuiviService {

    private static final Set<String> STATUTS_ETAPE = Set.of("A_FAIRE", "EN_COURS", "VALIDEE");

    /** Étapes par défaut du protocole de reprise (réathlétisation progressive). */
    private static final List<String> ETAPES_DEFAUT = List.of(
            "Repos & soins",
            "Course en ligne",
            "Changements de direction",
            "Travail avec ballon",
            "Entraînement collectif partiel",
            "Entraînement collectif complet",
            "Retour compétition");

    private final BlessureRepository blessureRepository;
    private final BlessureNoteRepository noteRepository;
    private final RtpEtapeRepository etapeRepository;
    private final ScopeResolver scopeResolver;
    private final CurrentUserProvider currentUser;

    public BlessureSuiviService(BlessureRepository blessureRepository, BlessureNoteRepository noteRepository,
                                RtpEtapeRepository etapeRepository, ScopeResolver scopeResolver,
                                CurrentUserProvider currentUser) {
        this.blessureRepository = blessureRepository;
        this.noteRepository = noteRepository;
        this.etapeRepository = etapeRepository;
        this.scopeResolver = scopeResolver;
        this.currentUser = currentUser;
    }

    // ──────────────────────────── Journal ────────────────────────────

    public List<NoteResponse> listerNotes(UUID blessureId) {
        verifierAcces(blessureId);
        return noteRepository.findByBlessureIdOrderByDateDescCreatedAtDesc(blessureId).stream()
                .map(this::toNote).toList();
    }

    public NoteResponse ajouterNote(UUID blessureId, NoteRequest req) {
        verifierAcces(blessureId);
        BlessureNote n = new BlessureNote();
        n.setBlessureId(blessureId);
        n.setDate(req.date() != null ? req.date() : LocalDate.now());
        n.setTexte(req.texte().trim());
        n.setDeposePar(currentUser.current().getId());
        return toNote(noteRepository.save(n));
    }

    public void supprimerNote(UUID blessureId, UUID noteId) {
        verifierAcces(blessureId);
        BlessureNote n = noteRepository.findById(noteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Note introuvable"));
        if (!n.getBlessureId().equals(blessureId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Note introuvable");
        }
        noteRepository.delete(n);
    }

    // ──────────────────────────── Protocole RTP ────────────────────────────

    public List<EtapeResponse> listerRtp(UUID blessureId) {
        verifierAcces(blessureId);
        return etapeRepository.findByBlessureIdOrderByOrdreAsc(blessureId).stream()
                .map(this::toEtape).toList();
    }

    /** Lecture du protocole par le joueur : seulement si la blessure lui appartient. */
    public List<EtapeResponse> listerRtpPourJoueur(UUID joueurId, UUID blessureId) {
        Blessure b = blessureRepository.findById(blessureId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blessure introuvable"));
        if (!b.getJoueurId().equals(joueurId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Blessure introuvable");
        }
        return etapeRepository.findByBlessureIdOrderByOrdreAsc(blessureId).stream()
                .map(this::toEtape).toList();
    }

    /** Crée le protocole par défaut (refuse si déjà initialisé). */
    public List<EtapeResponse> initialiserRtp(UUID blessureId) {
        verifierAcces(blessureId);
        if (etapeRepository.existsByBlessureId(blessureId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Protocole déjà initialisé");
        }
        short ordre = 1;
        for (String libelle : ETAPES_DEFAUT) {
            RtpEtape e = new RtpEtape();
            e.setBlessureId(blessureId);
            e.setOrdre(ordre++);
            e.setLibelle(libelle);
            e.setStatut("A_FAIRE");
            etapeRepository.save(e);
        }
        return listerRtp(blessureId);
    }

    public EtapeResponse majEtape(UUID blessureId, UUID etapeId, String statut) {
        verifierAcces(blessureId);
        String s = statut == null ? "" : statut.trim().toUpperCase();
        if (!STATUTS_ETAPE.contains(s)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Statut d'étape invalide");
        }
        RtpEtape e = etapeRepository.findById(etapeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Étape introuvable"));
        if (!e.getBlessureId().equals(blessureId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Étape introuvable");
        }
        e.setStatut(s);
        e.setDateValidation("VALIDEE".equals(s) ? LocalDate.now() : null);
        return toEtape(etapeRepository.save(e));
    }

    @Transactional
    public void supprimerRtp(UUID blessureId) {
        verifierAcces(blessureId);
        etapeRepository.deleteByBlessureId(blessureId);
    }

    // ──────────────────────────── Helpers ────────────────────────────

    private void verifierAcces(UUID blessureId) {
        Blessure b = blessureRepository.findById(blessureId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blessure introuvable"));
        scopeResolver.verifieAcces(b.getEquipeId());
    }

    private NoteResponse toNote(BlessureNote n) {
        return new NoteResponse(n.getId(), n.getBlessureId(), n.getDate(), n.getTexte(),
                n.getDeposePar(), n.getCreatedAt());
    }

    private EtapeResponse toEtape(RtpEtape e) {
        return new EtapeResponse(e.getId(), e.getBlessureId(), e.getOrdre(), e.getLibelle(),
                e.getStatut(), e.getDateValidation());
    }
}
