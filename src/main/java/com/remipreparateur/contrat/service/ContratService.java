package com.remipreparateur.contrat.service;

import com.remipreparateur.contrat.dto.ContratDtos.ContratRequest;
import com.remipreparateur.contrat.dto.ContratDtos.ContratResponse;
import com.remipreparateur.contrat.dto.ContratDtos.ContratStats;
import com.remipreparateur.contrat.dto.ContratDtos.MonContrat;
import com.remipreparateur.contrat.entity.Contrat;
import com.remipreparateur.contrat.repository.ContratRepository;
import com.remipreparateur.contrat.service.FichierContratStockage.Fichier;
import com.remipreparateur.contrat.service.FichierContratStockage.Stockage;
import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.saison.service.AppartenanceService;
import com.remipreparateur.shared.security.ScopeResolver;
import com.remipreparateur.shared.security.CurrentUserProvider;
import com.remipreparateur.shared.time.Horloge;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Contrats des personnes du club (fiches joueur ET staff). Gestion réservée à
 * contrats:manage (Président/Administratif, cf. SecurityConfig) ; la personne consulte
 * les SIENS via /api/membre (self-scope). Statut actif/expiré dérivé des dates
 * ({@link Horloge} → compatible date simulée).
 */
@Service
public class ContratService {

    private final ContratRepository repository;
    private final JoueurRepository joueurRepository;
    private final AppartenanceService appartenance;
    private final ScopeResolver scopeResolver;
    private final CurrentUserProvider currentUser;
    private final FichierContratStockage stockage;
    private final Horloge horloge;

    public ContratService(ContratRepository repository, JoueurRepository joueurRepository,
                          AppartenanceService appartenance, ScopeResolver scopeResolver,
                          CurrentUserProvider currentUser, FichierContratStockage stockage, Horloge horloge) {
        this.repository = repository;
        this.joueurRepository = joueurRepository;
        this.appartenance = appartenance;
        this.scopeResolver = scopeResolver;
        this.currentUser = currentUser;
        this.stockage = stockage;
        this.horloge = horloge;
    }

    // ──────────────────────────── Gestion (contrats:manage) ────────────────────────────

    public List<ContratResponse> lister() {
        UUID clubId = scopeResolver.clubActif();
        List<Contrat> contrats = repository.findByClubIdOrderByDateDebutDesc(clubId);
        Map<UUID, Joueur> fiches = fichesDe(contrats);
        return contrats.stream().map(c -> toResponse(c, fiches.get(c.getJoueurId()))).toList();
    }

    public ContratResponse creer(ContratRequest req) {
        UUID clubId = scopeResolver.clubActif();
        Joueur fiche = ficheDuClub(req.joueurId(), clubId);
        Contrat c = new Contrat();
        c.setClubId(clubId);
        c.setCreePar(currentUser.current().getId());
        appliquer(c, req);
        return toResponse(repository.save(c), fiche);
    }

    public ContratResponse modifier(UUID id, ContratRequest req) {
        Contrat c = contratChecke(id);
        Joueur fiche = ficheDuClub(req.joueurId(), c.getClubId());
        appliquer(c, req);
        return toResponse(repository.save(c), fiche);
    }

    public void supprimer(UUID id) {
        Contrat c = contratChecke(id);
        stockage.supprimer(c.getCheminStockage());
        repository.delete(c);
    }

    /** Joint (ou remplace) le PDF signé. */
    public ContratResponse deposerFichier(UUID id, MultipartFile fichier) {
        Contrat c = contratChecke(id);
        stockage.supprimer(c.getCheminStockage());
        Stockage s = stockage.stocker(fichier);
        c.setCheminStockage(s.nomPhysique());
        c.setNomOriginal(s.nomOriginal());
        c.setTypeMime(s.mime());
        c.setTailleOctets(s.taille());
        Joueur fiche = joueurRepository.findById(c.getJoueurId()).orElse(null);
        return toResponse(repository.save(c), fiche);
    }

    public Fichier chargerFichier(UUID id) {
        Contrat c = contratChecke(id);
        if (c.getCheminStockage() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun document joint");
        }
        return stockage.charger(c.getCheminStockage(), c.getNomOriginal(), c.getTypeMime());
    }

    /** Vue président : compteurs + contrats expirant sous 90 jours (triés par échéance). */
    public ContratStats stats() {
        UUID clubId = scopeResolver.clubActif();
        LocalDate today = horloge.today();
        List<Contrat> contrats = repository.findByClubIdOrderByDateDebutDesc(clubId);
        Map<UUID, Joueur> fiches = fichesDe(contrats);
        List<Contrat> actifs = contrats.stream().filter(c -> actif(c, today)).toList();
        List<ContratResponse> echeances = actifs.stream()
                .filter(c -> c.getDateFin() != null
                        && !c.getDateFin().isAfter(today.plusDays(90)))
                .sorted((a, b) -> a.getDateFin().compareTo(b.getDateFin()))
                .map(c -> toResponse(c, fiches.get(c.getJoueurId())))
                .toList();
        return new ContratStats(contrats.size(), actifs.size(), echeances.size(), echeances);
    }

    // ──────────────────────────── Espace personnel (self-scope) ────────────────────────────

    public List<MonContrat> mesContrats(UUID joueurId) {
        LocalDate today = horloge.today();
        return repository.findByJoueurIdOrderByDateDebutDesc(joueurId).stream()
                .map(c -> new MonContrat(c.getId(), c.getTypeContrat(), c.getDateDebut(), c.getDateFin(),
                        actif(c, today), c.getNomOriginal()))
                .toList();
    }

    public Fichier chargerMonFichier(UUID joueurId, UUID id) {
        Contrat c = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrat introuvable"));
        if (!c.getJoueurId().equals(joueurId) || c.getCheminStockage() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrat introuvable");
        }
        return stockage.charger(c.getCheminStockage(), c.getNomOriginal(), c.getTypeMime());
    }

    // ──────────────────────────── Helpers ────────────────────────────

    private void appliquer(Contrat c, ContratRequest req) {
        if (req.dateFin() != null && req.dateFin().isBefore(req.dateDebut())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date de fin avant la date de début");
        }
        c.setJoueurId(req.joueurId());
        c.setTypeContrat(req.typeContrat().trim());
        c.setDateDebut(req.dateDebut());
        c.setDateFin(req.dateFin());
        c.setNotes(req.notes() == null || req.notes().isBlank() ? null : req.notes().trim());
    }

    private boolean actif(Contrat c, LocalDate today) {
        return !c.getDateDebut().isAfter(today)
                && (c.getDateFin() == null || !c.getDateFin().isBefore(today));
    }

    private Contrat contratChecke(UUID id) {
        Contrat c = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrat introuvable"));
        scopeResolver.verifieAccesClub(c.getClubId());
        return c;
    }

    /** Fiche personne du club actif (joueur OU staff), 400 sinon. */
    private Joueur ficheDuClub(UUID joueurId, UUID clubId) {
        Joueur fiche = joueurRepository.findById(joueurId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fiche personne introuvable"));
        if (fiche.getClubId() != null && !fiche.getClubId().equals(clubId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fiche d'un autre club");
        }
        return fiche;
    }

    private Map<UUID, Joueur> fichesDe(List<Contrat> contrats) {
        return joueurRepository.findAllById(
                        contrats.stream().map(Contrat::getJoueurId).distinct().toList())
                .stream().collect(Collectors.toMap(Joueur::getId, Function.identity()));
    }

    private ContratResponse toResponse(Contrat c, Joueur fiche) {
        LocalDate today = horloge.today();
        boolean actif = actif(c, today);
        Integer joursRestants = c.getDateFin() == null ? null
                : (int) ChronoUnit.DAYS.between(today, c.getDateFin());
        return new ContratResponse(
                c.getId(), c.getJoueurId(),
                fiche != null ? fiche.getNom() : null,
                fiche != null ? fiche.getPrenom() : null,
                appartenance.equipePrincipale(c.getJoueurId()),
                c.getTypeContrat(), c.getDateDebut(), c.getDateFin(), actif, joursRestants,
                c.getNomOriginal(), c.getNotes(), c.getCreatedAt());
    }
}
