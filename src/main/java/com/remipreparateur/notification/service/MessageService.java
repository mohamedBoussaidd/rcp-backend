package com.remipreparateur.notification.service;

import com.remipreparateur.auth.entity.Role;
import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.notification.dto.MessageDtos.CapaciteEnvoiDto;
import com.remipreparateur.notification.dto.MessageDtos.MessageEnvoyeDto;
import com.remipreparateur.notification.dto.MessageDtos.MessageRequest;
import com.remipreparateur.notification.entity.NiveauEnvoi;
import com.remipreparateur.notification.entity.Priorite;
import com.remipreparateur.notification.entity.TypeNotification;
import com.remipreparateur.notification.repository.NotificationRepository;
import com.remipreparateur.saison.service.AppartenanceService;
import com.remipreparateur.shared.security.CurrentUserProvider;
import com.remipreparateur.shared.security.ScopeResolver;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Chat 1-sens. Le staff écrit à un joueur ou à toute l'équipe (émetteur = staff). Un joueur
 * autorisé écrit selon son niveau d'émission : EQUIPE (toute l'équipe) ou CIBLE (joueurs
 * précis). Chaque envoi groupe ses destinataires sous un {@code threadId} (évolutif → réponses).
 */
@Service
public class MessageService {

    private final CurrentUserProvider currentUser;
    private final ScopeResolver scopeResolver;
    private final NotificationDispatcher dispatcher;
    private final NotifConfigService configService;
    private final JoueurRepository joueurRepository;
    private final NotificationRepository notificationRepository;
    private final AppartenanceService appartenance;

    public MessageService(CurrentUserProvider currentUser, ScopeResolver scopeResolver,
                          NotificationDispatcher dispatcher, NotifConfigService configService,
                          JoueurRepository joueurRepository, NotificationRepository notificationRepository,
                          AppartenanceService appartenance) {
        this.currentUser = currentUser;
        this.scopeResolver = scopeResolver;
        this.dispatcher = dispatcher;
        this.appartenance = appartenance;
        this.configService = configService;
        this.joueurRepository = joueurRepository;
        this.notificationRepository = notificationRepository;
    }

    /** Qui peut envoyer (et avec quel périmètre) — pilote l'affichage du widget chat. */
    @Transactional(readOnly = true)
    public CapaciteEnvoiDto capacite() {
        Utilisateur u = currentUser.current();
        if (u.getRole() == Role.JOUEUR) {
            NiveauEnvoi niveau = u.getJoueurId() == null ? NiveauEnvoi.AUCUN
                    : configService.niveauEnvoi(u.getJoueurId());
            return new CapaciteEnvoiDto(niveau != NiveauEnvoi.AUCUN, false, niveau, niveau == NiveauEnvoi.CIBLE);
        }
        boolean staff = u.getRole() != Role.ADMINISTRATIF;
        return new CapaciteEnvoiDto(staff, true, null, staff);
    }

    /** Coéquipiers ciblables par l'utilisateur courant (staff : équipe active ; joueur CIBLE : son équipe). */
    @Transactional(readOnly = true)
    public List<com.remipreparateur.notification.dto.MessageDtos.DestinataireDto> destinataires() {
        Utilisateur u = currentUser.current();
        UUID equipe;
        if (u.getRole() == Role.JOUEUR) {
            if (u.getJoueurId() == null || configService.niveauEnvoi(u.getJoueurId()) != NiveauEnvoi.CIBLE) {
                return List.of();
            }
            equipe = u.getEquipeId();
        } else {
            equipe = scopeResolver.equipeActiveUnique();
        }
        if (equipe == null) return List.of();
        UUID monJoueurId = u.getJoueurId();
        return joueurRepository.findByEquipeIdIn(List.of(equipe)).stream()
                .filter(j -> !"inactif".equalsIgnoreCase(j.getStatut()))
                .filter(j -> !j.getId().equals(monJoueurId))
                .map(j -> new com.remipreparateur.notification.dto.MessageDtos.DestinataireDto(
                        j.getId(), (j.getPrenom() + " " + j.getNom()).trim()))
                .toList();
    }

    @Transactional
    public int envoyer(MessageRequest req) {
        Utilisateur u = currentUser.current();
        return (u.getRole() == Role.JOUEUR) ? envoyerDepuisJoueur(u, req) : envoyerDepuisStaff(u, req);
    }

    private int envoyerDepuisStaff(Utilisateur u, MessageRequest req) {
        UUID equipe = scopeResolver.equipeActiveUnique();
        UUID thread = UUID.randomUUID();
        String titre = titreOuDefaut(req, "Message du staff");
        if (req.destinataires() == null || req.destinataires().isEmpty()) {
            return dispatcher.versEquipeJoueurs(equipe, TypeNotification.MESSAGE_STAFF, titre,
                    req.corps(), "/joueur", u.getId(), thread, false);
        }
        return versCibles(equipe, req.destinataires(), TypeNotification.MESSAGE_STAFF, titre,
                req.corps(), u.getId(), thread);
    }

    private int envoyerDepuisJoueur(Utilisateur u, MessageRequest req) {
        if (u.getJoueurId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Compte non relié à une fiche joueur");
        }
        NiveauEnvoi niveau = configService.niveauEnvoi(u.getJoueurId());
        if (niveau == NiveauEnvoi.AUCUN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Envoi non autorisé");
        }
        UUID equipe = u.getEquipeId();
        UUID thread = UUID.randomUUID();
        String titre = titreOuDefaut(req, "Message d'un joueur");
        if (req.destinataires() == null || req.destinataires().isEmpty()) {
            return dispatcher.versEquipeJoueurs(equipe, TypeNotification.MESSAGE_JOUEUR, titre,
                    req.corps(), "/joueur", u.getId(), thread, false);
        }
        if (niveau != NiveauEnvoi.CIBLE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ciblage non autorisé (niveau ÉQUIPE)");
        }
        return versCibles(equipe, req.destinataires(), TypeNotification.MESSAGE_JOUEUR, titre,
                req.corps(), u.getId(), thread);
    }

    /** Envoie à des fiches joueur ciblées (toutes vérifiées dans l'équipe d'émission). */
    private int versCibles(UUID equipe, List<UUID> cibles, TypeNotification type, String titre,
                           String corps, UUID emetteurUserId, UUID thread) {
        int n = 0;
        for (UUID joueurId : cibles) {
            Joueur j = joueurRepository.findById(joueurId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Joueur introuvable"));
            if (equipe == null || !appartenance.equipesDe(j.getId()).contains(equipe)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Destinataire hors équipe");
            }
            if (dispatcher.versJoueurFiche(equipe, joueurId, type, titre, corps, "/joueur",
                    Priorite.NORMALE, emetteurUserId, thread, false)) {
                n++;
            }
        }
        return n;
    }

    /** Historique des messages envoyés par l'utilisateur courant (1 ligne par destinataire). */
    @Transactional(readOnly = true)
    public List<MessageEnvoyeDto> envoyes() {
        return notificationRepository.findByEmetteurUserIdOrderByCreatedAtDesc(
                        currentUser.current().getId(), PageRequest.of(0, 100)).getContent().stream()
                .map(n -> new MessageEnvoyeDto(n.getThreadId(), n.getTitre(), n.getCorps(), 1, n.getCreatedAt()))
                .toList();
    }

    private String titreOuDefaut(MessageRequest req, String defaut) {
        return (req.titre() == null || req.titre().isBlank()) ? defaut : req.titre().trim();
    }
}
