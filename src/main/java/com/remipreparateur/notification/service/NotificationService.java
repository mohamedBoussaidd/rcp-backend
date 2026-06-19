package com.remipreparateur.notification.service;

import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.auth.repository.UtilisateurRepository;
import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.notification.dto.NotificationDtos.CompteurResponse;
import com.remipreparateur.notification.dto.NotificationDtos.NotificationPage;
import com.remipreparateur.notification.dto.NotificationDtos.NotificationResponse;
import com.remipreparateur.notification.entity.*;
import com.remipreparateur.notification.repository.NotifPreferenceRepository;
import com.remipreparateur.notification.repository.NotificationRepository;
import com.remipreparateur.shared.security.CurrentUserProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Cœur des notifications in-app : lecture (liste paginée, compteur, marquage lu) côté
 * destinataire, et création unitaire {@link #delivrer} utilisée par les producteurs
 * (scheduler, événements, messages). La création respecte la préférence du destinataire
 * (type désactivé → non délivré). L'envoi Web Push est branché par {@link PushDispatcher}.
 */
@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final NotifPreferenceRepository preferenceRepository;
    private final JoueurRepository joueurRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final CurrentUserProvider currentUser;
    private final PushDispatcher pushDispatcher;

    public NotificationService(NotificationRepository repository,
                               NotifPreferenceRepository preferenceRepository,
                               JoueurRepository joueurRepository,
                               UtilisateurRepository utilisateurRepository,
                               CurrentUserProvider currentUser,
                               PushDispatcher pushDispatcher) {
        this.repository = repository;
        this.preferenceRepository = preferenceRepository;
        this.joueurRepository = joueurRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.currentUser = currentUser;
        this.pushDispatcher = pushDispatcher;
    }

    // ──────────────────────────── Lecture (destinataire) ────────────────────────────

    @Transactional(readOnly = true)
    public NotificationPage lister(int page, int size) {
        UUID userId = currentUser.current().getId();
        Page<Notification> p = repository.findByDestinataireUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(page, size));
        long nonLus = repository.countByDestinataireUserIdAndLuFalse(userId);
        Map<UUID, String> joueurNoms = new HashMap<>();
        Map<UUID, String> userNoms = new HashMap<>();
        return new NotificationPage(
                p.getContent().stream().map(n -> toResponse(n, joueurNoms, userNoms)).toList(),
                nonLus, page, p.isLast());
    }

    @Transactional(readOnly = true)
    public CompteurResponse compteurNonLus() {
        return new CompteurResponse(
                repository.countByDestinataireUserIdAndLuFalse(currentUser.current().getId()));
    }

    @Transactional
    public void marquerLu(UUID id) {
        Notification n = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification introuvable"));
        if (!n.getDestinataireUserId().equals(currentUser.current().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification introuvable");
        }
        if (!n.isLu()) {
            n.setLu(true);
            n.setLuAt(LocalDateTime.now());
            repository.save(n);
        }
    }

    @Transactional
    public void marquerToutLu() {
        repository.marquerToutLu(currentUser.current().getId(), LocalDateTime.now());
    }

    // ──────────────────────────── Création (producteurs) ────────────────────────────

    /**
     * Délivre une notification à UN destinataire si sa préférence ne l'a pas désactivée.
     * Persiste la ligne in-app puis déclenche l'envoi Web Push. Renvoie l'entité créée,
     * ou {@code Optional.empty()} si le destinataire a coupé ce type.
     */
    @Transactional
    public Optional<Notification> delivrer(Notification n) {
        if (!preferenceActive(n.getDestinataireUserId(), n.getType())) {
            return Optional.empty();
        }
        Notification saved = repository.save(n);
        pushDispatcher.envoyer(saved);
        return Optional.of(saved);
    }

    /** Le destinataire reçoit-il ce type ? (absence de préférence = actif par défaut). */
    public boolean preferenceActive(UUID userId, TypeNotification type) {
        return preferenceRepository.findByUserIdAndType(userId, type)
                .map(NotifPreference::isActif).orElse(true);
    }

    // ──────────────────────────── Mapping ────────────────────────────

    private NotificationResponse toResponse(Notification n, Map<UUID, String> joueurNoms,
                                            Map<UUID, String> userNoms) {
        String sujetNom = n.getSujetJoueurId() == null ? null
                : joueurNoms.computeIfAbsent(n.getSujetJoueurId(), this::nomJoueur);
        String emetteurNom = n.getEmetteurUserId() == null ? null
                : userNoms.computeIfAbsent(n.getEmetteurUserId(), this::nomUtilisateur);
        return new NotificationResponse(
                n.getId(), n.getType(), n.getType().categorie().name(),
                n.getTitre(), n.getCorps(), n.getLien(), n.getPriorite(),
                n.getEmetteurType(), emetteurNom,
                n.getSujetJoueurId(), sujetNom,
                n.getThreadId(), n.isRepondable(), n.isLu(), n.getCreatedAt());
    }

    private String nomJoueur(UUID id) {
        return joueurRepository.findById(id)
                .map(j -> (j.getPrenom() + " " + j.getNom()).trim()).orElse(null);
    }

    private String nomUtilisateur(UUID id) {
        return utilisateurRepository.findById(id)
                .map(u -> nomComplet(u)).orElse(null);
    }

    private String nomComplet(Utilisateur u) {
        String n = ((u.getPrenom() == null ? "" : u.getPrenom()) + " "
                + (u.getNom() == null ? "" : u.getNom())).trim();
        return n.isEmpty() ? u.getEmail() : n;
    }
}
