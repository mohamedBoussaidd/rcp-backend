package com.remipreparateur.notification.service;

import com.remipreparateur.auth.entity.Role;
import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.auth.repository.UtilisateurRepository;
import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.notification.dto.NotifConfigDtos.PreferenceDto;
import com.remipreparateur.notification.entity.NotifPreference;
import com.remipreparateur.notification.entity.TypeNotification;
import com.remipreparateur.notification.repository.NotifPreferenceRepository;
import com.remipreparateur.shared.security.CurrentUserProvider;
import com.remipreparateur.shared.security.ScopeResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Préférences de notification d'un destinataire. Deux niveaux :
 *  - le destinataire gère ses propres types (sauf ceux verrouillés par le staff) ;
 *  - le staff coupe un type pour un joueur ciblé et peut retirer au joueur le droit de
 *    modifier (verrou). Les types proposés dépendent du rôle du destinataire.
 */
@Service
public class NotifPreferenceService {

    private final NotifPreferenceRepository repository;
    private final UtilisateurRepository utilisateurRepository;
    private final JoueurRepository joueurRepository;
    private final CurrentUserProvider currentUser;
    private final ScopeResolver scopeResolver;

    public NotifPreferenceService(NotifPreferenceRepository repository,
                                  UtilisateurRepository utilisateurRepository,
                                  JoueurRepository joueurRepository,
                                  CurrentUserProvider currentUser, ScopeResolver scopeResolver) {
        this.repository = repository;
        this.utilisateurRepository = utilisateurRepository;
        this.joueurRepository = joueurRepository;
        this.currentUser = currentUser;
        this.scopeResolver = scopeResolver;
    }

    // ──────────────────────────── Destinataire (soi-même) ────────────────────────────

    @Transactional(readOnly = true)
    public List<PreferenceDto> listMine() {
        Utilisateur u = currentUser.current();
        // Politique « staff uniquement » : le joueur consulte en lecture seule ; le staff
        // gère ses propres notifications.
        boolean modifiable = u.getRole() != Role.JOUEUR;
        return prefsDe(u.getId(), u.getRole(), modifiable);
    }

    @Transactional
    public void updateMine(TypeNotification type, boolean actif) {
        Utilisateur u = currentUser.current();
        if (u.getRole() == Role.JOUEUR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Notifications gérées par le staff");
        }
        upsert(u.getId(), type, actif, false);
    }

    // ──────────────────────────── Staff → joueur ciblé ────────────────────────────

    @Transactional(readOnly = true)
    public List<PreferenceDto> listForJoueur(UUID joueurId) {
        Utilisateur u = compteJoueurChecke(joueurId);
        return prefsDe(u.getId(), Role.JOUEUR, true); // le staff peut modifier
    }

    @Transactional
    public void updateForJoueur(UUID joueurId, TypeNotification type, boolean actif, boolean verrouille) {
        Utilisateur u = compteJoueurChecke(joueurId);
        // Staff-only : toute préférence posée par le staff est verrouillée (le joueur ne la modifie pas).
        upsert(u.getId(), type, actif, true);
    }

    // ──────────────────────────── Matrice « par joueur » (staff) ────────────────────────────

    /** Préférences de TOUS les joueurs (à compte) de l'équipe active : types en colonnes. */
    @Transactional(readOnly = true)
    public com.remipreparateur.notification.dto.NotifConfigDtos.EquipeMatriceDto matriceEquipe() {
        UUID equipe = scopeResolver.equipeActiveUnique();
        List<TypeNotification> types = typesPour(Role.JOUEUR);
        List<String> typeNames = types.stream().map(Enum::name).toList();
        List<com.remipreparateur.notification.dto.NotifConfigDtos.LigneJoueurDto> lignes = new java.util.ArrayList<>();
        for (com.remipreparateur.joueur.entity.Joueur j : joueurRepository.findByEquipeIdIn(List.of(equipe))) {
            if ("inactif".equalsIgnoreCase(j.getStatut())) continue;
            Utilisateur u = utilisateurRepository.findByJoueurId(j.getId()).orElse(null);
            if (u == null) continue; // pas de compte → pas de préférences gérables
            java.util.Map<String, Boolean> actifs = new java.util.LinkedHashMap<>();
            for (TypeNotification t : types) {
                boolean actif = repository.findByUserIdAndType(u.getId(), t)
                        .map(NotifPreference::isActif).orElse(true);
                actifs.put(t.name(), actif);
            }
            lignes.add(new com.remipreparateur.notification.dto.NotifConfigDtos.LigneJoueurDto(
                    j.getId(), (j.getPrenom() + " " + j.getNom()).trim(), actifs));
        }
        return new com.remipreparateur.notification.dto.NotifConfigDtos.EquipeMatriceDto(typeNames, lignes);
    }

    /** Active/coupe un type pour TOUS les joueurs (à compte) de l'équipe active. */
    @Transactional
    public void setPourTypeEquipe(TypeNotification type, boolean actif) {
        UUID equipe = scopeResolver.equipeActiveUnique();
        for (com.remipreparateur.joueur.entity.Joueur j : joueurRepository.findByEquipeIdIn(List.of(equipe))) {
            if ("inactif".equalsIgnoreCase(j.getStatut())) continue;
            utilisateurRepository.findByJoueurId(j.getId())
                    .ifPresent(u -> upsert(u.getId(), type, actif, true));
        }
    }

    // ──────────────────────────── Interne ────────────────────────────

    private List<PreferenceDto> prefsDe(UUID userId, Role role, boolean modifiable) {
        return typesPour(role).stream().map(type -> {
            NotifPreference p = repository.findByUserIdAndType(userId, type).orElse(null);
            boolean actif = p == null || p.isActif();
            boolean verrou = p != null && p.isVerrouilleParStaff();
            return new PreferenceDto(type, type.categorie().name(), actif, verrou, modifiable);
        }).toList();
    }

    private void upsert(UUID userId, TypeNotification type, boolean actif, boolean verrouille) {
        NotifPreference p = repository.findByUserIdAndType(userId, type).orElseGet(NotifPreference::new);
        p.setUserId(userId);
        p.setType(type);
        p.setActif(actif);
        p.setVerrouilleParStaff(verrouille);
        repository.save(p);
    }

    /** Résout le compte relié à une fiche joueur, après vérification de portée d'équipe. */
    private Utilisateur compteJoueurChecke(UUID joueurId) {
        Joueur j = joueurRepository.findById(joueurId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Joueur introuvable"));
        scopeResolver.verifieAcces(j.getEquipeId());
        return utilisateurRepository.findByJoueurId(joueurId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ce joueur n'a pas de compte relié"));
    }

    /** Types pertinents pour un rôle (un joueur ne règle pas les alertes staff, et inversement). */
    private List<TypeNotification> typesPour(Role role) {
        boolean joueur = role == Role.JOUEUR;
        return java.util.Arrays.stream(TypeNotification.values())
                .filter(t -> joueur ? estPourJoueur(t) : t.pourStaff())
                .toList();
    }

    private boolean estPourJoueur(TypeNotification t) {
        return switch (t.categorie()) {
            case RAPPEL, INFO -> true;
            case MESSAGE -> t == TypeNotification.MESSAGE_STAFF;
            default -> false;
        };
    }
}
