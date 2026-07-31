package com.remipreparateur.performance.evenement.service;

import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.performance.evenement.dto.EvenementDtos.EvenementRequest;
import com.remipreparateur.performance.evenement.dto.EvenementDtos.EvenementResponse;
import com.remipreparateur.performance.evenement.dto.EvenementDtos.PersonneConcernee;
import com.remipreparateur.performance.evenement.entity.Evenement;
import com.remipreparateur.performance.evenement.repository.EvenementRepository;
import com.remipreparateur.notification.service.NotificationProducer;
import com.remipreparateur.saison.service.AppartenanceService;
import com.remipreparateur.shared.security.CurrentUserProvider;
import com.remipreparateur.shared.security.Scope;
import com.remipreparateur.shared.security.ScopeResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Événements extrasportifs. Lecture scopée au club actif puis filtrée sur les équipes
 * autorisées ; écriture réservée au staff qui peut déjà écrire des séances.
 */
@Service
public class EvenementService {

    private static final Set<String> TYPES = Set.of(
            "VIE_CLUB", "DEPLACEMENT", "SCOLAIRE", "CONVIVIALITE", "RENDEZ_VOUS", "INDISPONIBILITE", "AUTRE");

    private final EvenementRepository repository;
    private final JoueurRepository joueurRepository;
    private final ScopeResolver scopeResolver;
    private final CurrentUserProvider currentUser;
    private final NotificationProducer notificationProducer;
    private final AppartenanceService appartenance;

    public EvenementService(EvenementRepository repository, JoueurRepository joueurRepository,
                            ScopeResolver scopeResolver, CurrentUserProvider currentUser,
                            NotificationProducer notificationProducer,
                            AppartenanceService appartenance) {
        this.repository = repository;
        this.joueurRepository = joueurRepository;
        this.scopeResolver = scopeResolver;
        this.currentUser = currentUser;
        this.notificationProducer = notificationProducer;
        this.appartenance = appartenance;
    }

    /** Événements visibles par le staff sur la période (club actif, équipes autorisées). */
    @Transactional(readOnly = true)
    public List<EvenementResponse> lister(LocalDate debut, LocalDate fin) {
        UUID club = scopeResolver.clubActif();
        if (club == null) return List.of();
        List<Evenement> rows = repository.findChevauchant(club, debut, fin).stream()
                .filter(e -> e.getEquipeId() == null || scopeResolver.peutAcceder(e.getEquipeId()))
                .toList();
        return enrichir(rows);
    }

    /**
     * Événements visibles par UN joueur : ceux de ses équipes (ou du club entier) qui lui sont
     * ouverts, et — s'ils ciblent des personnes — uniquement ceux où il figure. Un événement
     * marqué non visible des joueurs reste strictement interne au staff.
     */
    @Transactional(readOnly = true)
    public List<EvenementResponse> listerPourJoueur(UUID joueurId, LocalDate debut, LocalDate fin) {
        Joueur joueur = joueurRepository.findById(joueurId).orElse(null);
        if (joueur == null || joueur.getClubId() == null) return List.of();
        Scope scope = scopeResolver.resolve();
        List<Evenement> rows = repository.findChevauchant(joueur.getClubId(), debut, fin).stream()
                .filter(Evenement::isVisibleJoueurs)
                .filter(e -> e.getEquipeId() == null || scope.all() || scope.equipeIds().contains(e.getEquipeId()))
                .filter(e -> e.getJoueurIds().isEmpty() || e.getJoueurIds().contains(joueurId))
                .toList();
        return enrichir(rows);
    }

    @Transactional
    public EvenementResponse creer(EvenementRequest req) {
        Evenement e = new Evenement();
        // `clubEntierPourGestion()` ne répond QUE pour président / administratif / super-admin
        // avec contexte : un entraîneur ou un préparateur obtenait null, donc une violation de
        // contrainte NOT NULL à l'insertion. `clubActif()` résout le club depuis le contexte,
        // le compte ou l'équipe, et lève un 409 explicite s'il reste indéterminable.
        e.setClubId(scopeResolver.clubActif());
        e.setCreePar(currentUser.current().getId());
        appliquer(e, req);
        Evenement saved = repository.save(e);
        notifierCibles(saved, Set.of());
        return enrichir(List.of(saved)).get(0);
    }

    @Transactional
    public EvenementResponse modifier(UUID id, EvenementRequest req) {
        Evenement e = charger(id);
        Set<UUID> avant = new LinkedHashSet<>(e.getJoueurIds());
        appliquer(e, req);
        Evenement saved = repository.save(e);
        // Seules les personnes NOUVELLEMENT ciblées sont notifiées : corriger une faute de frappe
        // dans le titre ne doit pas re-sonner chez tout le monde.
        notifierCibles(saved, avant);
        return enrichir(List.of(saved)).get(0);
    }

    /**
     * Notifie les personnes nommément concernées (hors {@code dejaNotifiees}). Un événement
     * d'équipe ne notifie personne : il vit dans le calendrier, et sur-notifier est le meilleur
     * moyen de faire désactiver les notifications.
     */
    private void notifierCibles(Evenement e, Set<UUID> dejaNotifiees) {
        if (!e.isVisibleJoueurs() || e.getJoueurIds().isEmpty()) return;
        String quand = e.getDate().toString()
                + (e.getDateFin() != null ? " → " + e.getDateFin() : "")
                + (e.getHeureDebut() != null ? " à " + e.getHeureDebut() : "");
        for (UUID joueurId : e.getJoueurIds()) {
            if (dejaNotifiees.contains(joueurId)) continue;
            // `notification.equipe_id` est NOT NULL : un événement de club (equipeId null) doit
            // donc être notifié SUR L'ÉQUIPE DU DESTINATAIRE, pas sur celle de l'événement.
            // Sans ça l'insert échouait et faisait échouer la création de l'événement entier.
            UUID equipe = e.getEquipeId() != null ? e.getEquipeId() : appartenance.equipePrincipale(joueurId);
            if (equipe == null) continue;   // personne hors effectif : pas de canal de notification
            notificationProducer.evenementCible(equipe, joueurId, e.getTitre(), quand, e.getLieu());
        }
    }

    @Transactional
    public void supprimer(UUID id) {
        repository.delete(charger(id));
    }

    private Evenement charger(UUID id) {
        Evenement e = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Événement introuvable"));
        scopeResolver.verifieAccesClub(e.getClubId());
        if (e.getEquipeId() != null) scopeResolver.verifieAcces(e.getEquipeId());
        return e;
    }

    private void appliquer(Evenement e, EvenementRequest req) {
        String type = req.type() == null ? "AUTRE" : req.type().trim().toUpperCase();
        if (!TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type d'événement inconnu : " + type);
        }
        if (req.dateFin() != null && req.dateFin().isBefore(req.date())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La date de fin est antérieure à la date de début.");
        }
        // Équipe explicite → contrôlée ; absente → l'événement porte sur tout le club (equipeId null),
        // ce qui reste volontairement possible (assemblée générale, tournoi du club…).
        if (req.equipeId() != null) scopeResolver.verifieAcces(req.equipeId());
        e.setEquipeId(req.equipeId());
        e.setType(type);
        e.setTitre(req.titre().trim());
        e.setDate(req.date());
        e.setDateFin(req.dateFin());
        e.setHeureDebut(req.heureDebut());
        e.setHeureFin(req.heureFin());
        e.setLieu(videEnNull(req.lieu()));
        e.setDescription(videEnNull(req.description()));
        e.setVisibleJoueurs(req.visibleJoueurs() == null || req.visibleJoueurs());
        e.setJoueurIds(req.joueurIds() == null ? new LinkedHashSet<>() : new LinkedHashSet<>(req.joueurIds()));
    }

    /** Résout les personnes ciblées en UNE requête pour tout le lot (et non une par événement). */
    private List<EvenementResponse> enrichir(List<Evenement> rows) {
        Set<UUID> ids = rows.stream().flatMap(e -> e.getJoueurIds().stream()).collect(Collectors.toSet());
        Map<UUID, Joueur> personnes = ids.isEmpty() ? Map.of()
                : joueurRepository.findAllById(ids).stream()
                        .collect(Collectors.toMap(Joueur::getId, Function.identity()));
        return rows.stream().map(e -> new EvenementResponse(
                e.getId(), e.getType(), e.getTitre(), e.getDate(), e.getDateFin(),
                e.getHeureDebut(), e.getHeureFin(), e.getLieu(), e.getDescription(),
                e.getEquipeId(), e.isVisibleJoueurs(),
                e.getJoueurIds().stream()
                        .map(personnes::get)
                        .filter(java.util.Objects::nonNull)
                        .map(j -> new PersonneConcernee(j.getId(), j.getNom(), j.getPrenom()))
                        .toList()))
                .toList();
    }

    private String videEnNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
