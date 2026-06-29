package com.remipreparateur.notification.service;

import com.remipreparateur.auth.entity.Role;
import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.auth.repository.UtilisateurRepository;
import com.remipreparateur.notification.entity.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Aiguillage (fan-out) des notifications : vers le staff d'une équipe selon le routage par
 * rôle ({@link NotifConfigService}), vers un joueur précis, ou vers tous les joueurs d'une
 * équipe. Chaque destinataire reçoit une ligne via {@link NotificationService#delivrer}.
 */
@Service
public class NotificationDispatcher {

    private final NotificationService notificationService;
    private final NotifConfigService configService;
    private final UtilisateurRepository utilisateurRepository;

    public NotificationDispatcher(NotificationService notificationService,
                                  NotifConfigService configService,
                                  UtilisateurRepository utilisateurRepository) {
        this.notificationService = notificationService;
        this.configService = configService;
        this.utilisateurRepository = utilisateurRepository;
    }

    /** Diffuse une alerte/digest au staff de l'équipe (rôles définis par le routage). */
    public int versStaff(UUID equipeId, TypeNotification type, String titre, String corps,
                         String lien, UUID sujetJoueurId, Priorite priorite) {
        List<Role> roles = new ArrayList<>();
        for (String r : configService.rolesPour(equipeId, type)) {
            try { roles.add(Role.valueOf(r.trim())); } catch (IllegalArgumentException ignore) { }
        }
        if (roles.isEmpty()) return 0;
        int n = 0;
        for (Utilisateur u : utilisateurRepository.findByEquipeIdAndRoleIn(equipeId, roles)) {
            Notification notif = base(equipeId, u.getId(), type, titre, corps, lien, priorite);
            notif.setSujetJoueurId(sujetJoueurId);
            if (notificationService.delivrer(notif).isPresent()) n++;
        }
        return n;
    }

    /** Diffuse à des rôles staff EXPLICITES de l'équipe (indépendant du routage configurable). */
    public int versStaffRoles(UUID equipeId, List<Role> roles, TypeNotification type, String titre,
                              String corps, String lien, Priorite priorite) {
        if (roles == null || roles.isEmpty()) return 0;
        int n = 0;
        for (Utilisateur u : utilisateurRepository.findByEquipeIdAndRoleIn(equipeId, roles)) {
            Notification notif = base(equipeId, u.getId(), type, titre, corps, lien, priorite);
            if (notificationService.delivrer(notif).isPresent()) n++;
        }
        return n;
    }

    /** Notifie un joueur (via son compte) ; no-op si la fiche n'est reliée à aucun compte. */
    public boolean versJoueurFiche(UUID equipeId, UUID joueurId, TypeNotification type,
                                   String titre, String corps, String lien, Priorite priorite,
                                   UUID emetteurUserId, UUID threadId, boolean repondable) {
        return utilisateurRepository.findByJoueurId(joueurId).map(u -> {
            Notification notif = base(equipeId, u.getId(), type, titre, corps, lien, priorite);
            notif.setSujetJoueurId(joueurId);
            if (emetteurUserId != null) {
                notif.setEmetteurType(EmetteurType.UTILISATEUR);
                notif.setEmetteurUserId(emetteurUserId);
            }
            notif.setThreadId(threadId);
            notif.setRepondable(repondable);
            return notificationService.delivrer(notif).isPresent();
        }).orElse(false);
    }

    /** Diffuse à tous les comptes JOUEUR de l'équipe. */
    public int versEquipeJoueurs(UUID equipeId, TypeNotification type, String titre, String corps,
                                 String lien, UUID emetteurUserId, UUID threadId, boolean repondable) {
        int n = 0;
        for (Utilisateur u : utilisateurRepository.findByEquipeIdAndRoleIn(equipeId, List.of(Role.JOUEUR))) {
            Notification notif = base(equipeId, u.getId(), type, titre, corps, lien, Priorite.NORMALE);
            if (emetteurUserId != null) {
                notif.setEmetteurType(EmetteurType.UTILISATEUR);
                notif.setEmetteurUserId(emetteurUserId);
            }
            notif.setThreadId(threadId);
            notif.setRepondable(repondable);
            if (notificationService.delivrer(notif).isPresent()) n++;
        }
        return n;
    }

    private Notification base(UUID equipeId, UUID destUserId, TypeNotification type, String titre,
                              String corps, String lien, Priorite priorite) {
        Notification n = new Notification();
        n.setEquipeId(equipeId);
        n.setDestinataireUserId(destUserId);
        n.setType(type);
        n.setTitre(titre);
        n.setCorps(corps);
        n.setLien(lien);
        n.setPriorite(priorite == null ? Priorite.NORMALE : priorite);
        return n;
    }
}
