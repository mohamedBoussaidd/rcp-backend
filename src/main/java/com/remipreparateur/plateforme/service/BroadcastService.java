package com.remipreparateur.plateforme.service;

import com.remipreparateur.auth.entity.Role;
import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.club.entity.Club;
import com.remipreparateur.club.entity.Equipe;
import com.remipreparateur.club.repository.ClubRepository;
import com.remipreparateur.club.repository.EquipeRepository;
import com.remipreparateur.auth.repository.UtilisateurRepository;
import com.remipreparateur.notification.entity.EmetteurType;
import com.remipreparateur.notification.entity.Notification;
import com.remipreparateur.notification.entity.Priorite;
import com.remipreparateur.notification.entity.TypeNotification;
import com.remipreparateur.notification.service.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Diffusion d'une annonce ({@link TypeNotification#ANNONCE}) par le super-admin, vers tous les
 * clubs, un club précis, ou un rôle. Le fan-out {@code notification} exige une équipe d'ancrage
 * (contrainte technique) : on prend la première équipe du club, sans impact pour le destinataire
 * qui ne voit ses notifications que par son propre identifiant. Les clubs sans équipe sont ignorés.
 */
@Service
public class BroadcastService {

    public enum Cible { TOUS, CLUB, ROLE }

    private final ClubRepository clubRepository;
    private final EquipeRepository equipeRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final NotificationService notificationService;

    public BroadcastService(ClubRepository clubRepository, EquipeRepository equipeRepository,
                            UtilisateurRepository utilisateurRepository,
                            NotificationService notificationService) {
        this.clubRepository = clubRepository;
        this.equipeRepository = equipeRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.notificationService = notificationService;
    }

    /** Diffuse l'annonce. Renvoie le nombre de destinataires effectivement notifiés. */
    @Transactional
    public int diffuser(Cible cible, UUID clubId, Role role, String titre, String corps,
                        String lien, Priorite priorite, UUID emetteurUserId) {
        List<Club> clubs = (cible == Cible.CLUB && clubId != null)
                ? clubRepository.findById(clubId).map(List::of).orElse(List.of())
                : clubRepository.findAll();

        int n = 0;
        for (Club club : clubs) {
            UUID ancre = equipeRepository.findByClubId(club.getId()).stream()
                    .findFirst().map(Equipe::getId).orElse(null);
            if (ancre == null) continue; // pas d'équipe = pas d'ancrage possible

            List<Utilisateur> destinataires = (cible == Cible.ROLE && role != null)
                    ? utilisateurRepository.findByClubIdAndRoleIn(club.getId(), List.of(role))
                    : utilisateurRepository.findByClubId(club.getId());

            for (Utilisateur u : destinataires) {
                if (!u.isActif() || u.getRole() == Role.SUPER_ADMIN) continue;
                Notification notif = new Notification();
                notif.setEquipeId(ancre);
                notif.setDestinataireUserId(u.getId());
                notif.setType(TypeNotification.ANNONCE);
                notif.setTitre(titre);
                notif.setCorps(corps);
                notif.setLien(lien);
                notif.setPriorite(priorite == null ? Priorite.NORMALE : priorite);
                notif.setEmetteurType(EmetteurType.UTILISATEUR);
                notif.setEmetteurUserId(emetteurUserId);
                if (notificationService.delivrer(notif).isPresent()) n++;
            }
        }
        return n;
    }
}
