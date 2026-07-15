package com.remipreparateur.medical.blessure.service;

import com.remipreparateur.medical.blessure.dto.BlessureSuiviDtos.EtapeCreateRequest;
import com.remipreparateur.medical.blessure.dto.BlessureSuiviDtos.EtapeResponse;
import com.remipreparateur.medical.blessure.dto.BlessureSuiviDtos.EtapeUpdateRequest;
import com.remipreparateur.medical.blessure.dto.BlessureSuiviDtos.NoteRequest;
import com.remipreparateur.medical.blessure.dto.BlessureSuiviDtos.NoteResponse;
import com.remipreparateur.medical.blessure.entity.Blessure;
import com.remipreparateur.medical.blessure.entity.BlessureNote;
import com.remipreparateur.medical.protocole.dto.ProtocoleModeleDtos.ModeleResponse;
import com.remipreparateur.medical.protocole.entity.ProtocoleModele;
import com.remipreparateur.medical.protocole.entity.ProtocoleModeleEtape;
import com.remipreparateur.medical.protocole.service.ProtocoleModeleService;
import com.remipreparateur.medical.rtp.entity.RtpEtape;
import com.remipreparateur.medical.blessure.repository.BlessureNoteRepository;
import com.remipreparateur.medical.blessure.repository.BlessureRepository;
import com.remipreparateur.medical.rtp.repository.RtpEtapeRepository;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.shared.security.CurrentUserProvider;
import com.remipreparateur.shared.security.ScopeResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Suivi de blessure : journal d'évolution (notes) + protocole de retour au jeu (RTP).
 * Accès calqué sur les blessures : lecture staff (scope équipe), écriture MEDICAL
 * (cf. SecurityConfig sur /api/blessures/**).
 *
 * <p>Le protocole s'initialise en CLONANT les étapes d'un {@link ProtocoleModele} du club
 * (choisi ou suggéré) : aucun lien persistant, l'édition d'un protocole en cours ne touche
 * jamais le modèle (et réciproquement). Les étapes sont ensuite librement éditables.
 */
@Service
public class BlessureSuiviService {

    private static final Set<String> STATUTS_ETAPE = Set.of("A_FAIRE", "EN_COURS", "VALIDEE");

    private final BlessureRepository blessureRepository;
    private final BlessureNoteRepository noteRepository;
    private final RtpEtapeRepository etapeRepository;
    private final JoueurRepository joueurRepository;
    private final ProtocoleModeleService protocoleService;
    private final ScopeResolver scopeResolver;
    private final CurrentUserProvider currentUser;

    public BlessureSuiviService(BlessureRepository blessureRepository, BlessureNoteRepository noteRepository,
                                RtpEtapeRepository etapeRepository, JoueurRepository joueurRepository,
                                ProtocoleModeleService protocoleService, ScopeResolver scopeResolver,
                                CurrentUserProvider currentUser) {
        this.blessureRepository = blessureRepository;
        this.noteRepository = noteRepository;
        this.etapeRepository = etapeRepository;
        this.joueurRepository = joueurRepository;
        this.protocoleService = protocoleService;
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

    /** Modèle suggéré pour la blessure (critères type/zone/gravité), ou null si aucun. */
    public ModeleResponse suggestion(UUID blessureId) {
        Blessure b = blessureChecke(blessureId);
        ProtocoleModele m = protocoleService.suggerer(clubDe(b), b.getTypeBlessure(),
                b.getZoneCorporelle(), b.getGravite());
        return m == null ? null : protocoleService.toResponse(m);
    }

    /**
     * Initialise le protocole en clonant les étapes du modèle demandé (ou, sans {@code modeleId},
     * du modèle suggéré). Refuse si déjà initialisé.
     */
    @Transactional
    public List<EtapeResponse> initialiserRtp(UUID blessureId, UUID modeleId) {
        Blessure b = blessureChecke(blessureId);
        if (etapeRepository.existsByBlessureId(blessureId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Protocole déjà initialisé");
        }
        UUID clubId = clubDe(b);
        ProtocoleModele modele;
        if (modeleId != null) {
            modele = protocoleService.modeleChecke(modeleId);
            if (!modele.getClubId().equals(clubId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Modèle d'un autre club");
            }
        } else {
            modele = protocoleService.suggerer(clubId, b.getTypeBlessure(), b.getZoneCorporelle(), b.getGravite());
            if (modele == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Aucun modèle de protocole actif dans le club");
            }
        }
        List<ProtocoleModeleEtape> etapesModele = protocoleService.etapesDe(modele.getId());
        if (etapesModele.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Le modèle choisi n'a aucune étape");
        }
        short ordre = 1;
        for (ProtocoleModeleEtape em : etapesModele) {
            RtpEtape e = new RtpEtape();
            e.setBlessureId(blessureId);
            e.setOrdre(ordre++);
            e.setLibelle(em.getLibelle());
            e.setJDebut(em.getJDebut());
            e.setJFin(em.getJFin());
            e.setDescription(em.getDescription());
            e.setStatut("A_FAIRE");
            etapeRepository.save(e);
        }
        return listerRtp(blessureId);
    }

    /** Ajoute une étape en fin de protocole. */
    public EtapeResponse ajouterEtape(UUID blessureId, EtapeCreateRequest req) {
        verifierAcces(blessureId);
        List<RtpEtape> existantes = etapeRepository.findByBlessureIdOrderByOrdreAsc(blessureId);
        if (existantes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Protocole non initialisé");
        }
        RtpEtape e = new RtpEtape();
        e.setBlessureId(blessureId);
        e.setOrdre((short) (existantes.get(existantes.size() - 1).getOrdre() + 1));
        e.setLibelle(req.libelle().trim());
        e.setJDebut(req.jDebut());
        e.setJFin(req.jFin());
        e.setDescription(videEnNull(req.description()));
        e.setStatut("A_FAIRE");
        return toEtape(etapeRepository.save(e));
    }

    /** Édition partielle d'une étape : statut et/ou contenu (libellé, fenêtre J, description). */
    public EtapeResponse modifierEtape(UUID blessureId, UUID etapeId, EtapeUpdateRequest req) {
        verifierAcces(blessureId);
        RtpEtape e = etapeChecke(blessureId, etapeId);
        if (req.statut() != null) {
            String s = req.statut().trim().toUpperCase();
            if (!STATUTS_ETAPE.contains(s)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Statut d'étape invalide");
            }
            e.setStatut(s);
            e.setDateValidation("VALIDEE".equals(s) ? LocalDate.now() : null);
        }
        if (req.libelle() != null) {
            if (req.libelle().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Libellé vide");
            }
            e.setLibelle(req.libelle().trim());
        }
        if (req.jDebut() != null) e.setJDebut(req.jDebut() < 0 ? null : req.jDebut());
        if (req.jFin() != null) e.setJFin(req.jFin() < 0 ? null : req.jFin());
        if (req.description() != null) e.setDescription(videEnNull(req.description()));
        return toEtape(etapeRepository.save(e));
    }

    public void supprimerEtape(UUID blessureId, UUID etapeId) {
        verifierAcces(blessureId);
        etapeRepository.delete(etapeChecke(blessureId, etapeId));
    }

    /** Réordonne le protocole : la liste doit être une permutation complète des étapes. */
    @Transactional
    public List<EtapeResponse> reordonner(UUID blessureId, List<UUID> etapeIds) {
        verifierAcces(blessureId);
        List<RtpEtape> existantes = etapeRepository.findByBlessureIdOrderByOrdreAsc(blessureId);
        Set<UUID> attendus = new HashSet<>(existantes.stream().map(RtpEtape::getId).toList());
        if (etapeIds.size() != existantes.size() || !attendus.equals(new HashSet<>(etapeIds))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Liste d'étapes incomplète ou inconnue");
        }
        short ordre = 1;
        for (UUID id : etapeIds) {
            RtpEtape e = existantes.stream().filter(x -> x.getId().equals(id)).findFirst().orElseThrow();
            e.setOrdre(ordre++);
            etapeRepository.save(e);
        }
        return listerRtp(blessureId);
    }

    @Transactional
    public void supprimerRtp(UUID blessureId) {
        verifierAcces(blessureId);
        etapeRepository.deleteByBlessureId(blessureId);
    }

    // ──────────────────────────── Helpers ────────────────────────────

    private void verifierAcces(UUID blessureId) {
        blessureChecke(blessureId);
    }

    private Blessure blessureChecke(UUID blessureId) {
        Blessure b = blessureRepository.findById(blessureId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blessure introuvable"));
        scopeResolver.verifieAcces(b.getEquipeId());
        return b;
    }

    private UUID clubDe(Blessure b) {
        return joueurRepository.findById(b.getJoueurId())
                .map(j -> j.getClubId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Fiche joueur introuvable"));
    }

    private RtpEtape etapeChecke(UUID blessureId, UUID etapeId) {
        RtpEtape e = etapeRepository.findById(etapeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Étape introuvable"));
        if (!e.getBlessureId().equals(blessureId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Étape introuvable");
        }
        return e;
    }

    private String videEnNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private NoteResponse toNote(BlessureNote n) {
        return new NoteResponse(n.getId(), n.getBlessureId(), n.getDate(), n.getTexte(),
                n.getDeposePar(), n.getCreatedAt());
    }

    private EtapeResponse toEtape(RtpEtape e) {
        return new EtapeResponse(e.getId(), e.getBlessureId(), e.getOrdre(), e.getLibelle(),
                e.getStatut(), e.getDateValidation(), e.getJDebut(), e.getJFin(), e.getDescription());
    }
}
