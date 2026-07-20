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
        return versStaffRoles(equipeId, roles, type, titre, corps, lien, priorite, null);
    }

    /** Variante avec émetteur exclu (ex. partage de séance : l'auteur ne se notifie pas lui-même). */
    public int versStaffRoles(UUID equipeId, List<Role> roles, TypeNotification type, String titre,
                              String corps, String lien, Priorite priorite, UUID exclureUserId) {
        if (roles == null || roles.isEmpty()) return 0;
        int n = 0;
        for (Utilisateur u : utilisateurRepository.findByEquipeIdAndRoleIn(equipeId, roles)) {
            if (u.getId().equals(exclureUserId)) continue;
            Notification notif = base(equipeId, u.getId(), type, titre, corps, lien, priorite);
            if (exclureUserId != null) {
                notif.setEmetteurType(EmetteurType.UTILISATEUR);
                notif.setEmetteurUserId(exclureUserId);
            }
            if (notificationService.delivrer(notif).isPresent()) n++;
        }
        return n;
    }

    /**
     * Diffuse à des rôles staff rattachés au CLUB (Président/Administratif), pas à une équipe
     * précise. La table {@code notification} exige une équipe (contrainte technique de fan-out
     * par équipe) : {@code equipeAncre} — n'importe quelle équipe du club — sert d'ancrage sans
     * aucun impact pour le destinataire, qui ne voit ses notifications que par son propre
     * identifiant (cf. {@code NotificationController}), jamais filtrées par équipe.
     */
    public int versStaffRolesClub(UUID clubId, UUID equipeAncre, List<Role> roles, TypeNotification type,
                                  String titre, String corps, String lien, Priorite priorite) {
        if (roles == null || roles.isEmpty() || equipeAncre == null) return 0;
        int n = 0;
        for (Utilisateur u : utilisateurRepository.findByClubIdAndRoleIn(clubId, roles)) {
            Notification notif = base(equipeAncre, u.getId(), type, titre, corps, lien, priorite);
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
