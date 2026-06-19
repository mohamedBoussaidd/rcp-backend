package com.remipreparateur.club.service;

import com.remipreparateur.club.dto.GestionDtos.*;
import com.remipreparateur.club.entity.Club;
import com.remipreparateur.club.entity.Equipe;
import com.remipreparateur.auth.entity.Role;
import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.club.repository.ClubRepository;
import com.remipreparateur.club.repository.EquipeRepository;
import com.remipreparateur.auth.repository.UtilisateurRepository;
import com.remipreparateur.auth.rbac.AffectationRole;
import com.remipreparateur.auth.rbac.AffectationRoleRepository;
import com.remipreparateur.auth.rbac.RoleApplicatifRepository;
import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.shared.security.ContexteActif;
import com.remipreparateur.shared.security.ContexteActifHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Gestion des equipes et membres d'un club, du point de vue de son president. */
@Service
public class GestionClubService {

    private static final int MAX_EQUIPES = 3;
    private static final Set<Role> ROLES_MEMBRES = EnumSet.of(
            Role.ENTRAINEUR, Role.PREPARATEUR, Role.MEDICAL, Role.ADMINISTRATIF, Role.JOUEUR);
    /** Rôles staff dotés d'une affectation RBAC « principale » (le joueur reste en self-scope). */
    private static final Set<Role> ROLES_STAFF = EnumSet.of(
            Role.ENTRAINEUR, Role.PREPARATEUR, Role.MEDICAL, Role.ADMINISTRATIF);

    private final EquipeRepository equipeRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ClubRepository clubRepository;
    private final JoueurRepository joueurRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleApplicatifRepository roleRepository;
    private final AffectationRoleRepository affectationRepository;

    public GestionClubService(EquipeRepository equipeRepository,
                              UtilisateurRepository utilisateurRepository,
                              ClubRepository clubRepository,
                              JoueurRepository joueurRepository,
                              PasswordEncoder passwordEncoder,
                              RoleApplicatifRepository roleRepository,
                              AffectationRoleRepository affectationRepository) {
        this.equipeRepository = equipeRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.clubRepository = clubRepository;
        this.joueurRepository = joueurRepository;
        this.passwordEncoder = passwordEncoder;
        this.roleRepository = roleRepository;
        this.affectationRepository = affectationRepository;
    }

    // ── Vue agregee ──
    public MonClubResponse monClub(Utilisateur president) {
        UUID clubId = exigeClub(president);
        Club club = clubRepository.findById(clubId).orElse(null);
        return new MonClubResponse(
                clubId,
                club != null ? club.getNom() : null,
                club != null ? club.getLogo() : null,
                listerEquipes(clubId),
                listerMembres(clubId));
    }

    // ── Equipes ──
    @Transactional
    public EquipeResponse creerEquipe(Utilisateur president, EquipeRequest req) {
        UUID clubId = exigeClub(president);
        if (equipeRepository.countByClubId(clubId) >= MAX_EQUIPES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Maximum " + MAX_EQUIPES + " equipes par club");
        }
        Equipe e = new Equipe();
        e.setNom(req.nom());
        e.setCategorie(req.categorie());
        e.setClubId(clubId);
        e = equipeRepository.save(e);
        return toEquipeResponse(e);
    }

    public List<EquipeResponse> listerEquipes(UUID clubId) {
        return equipeRepository.findByClubId(clubId).stream().map(this::toEquipeResponse).toList();
    }

    @Transactional
    public EquipeResponse modifierEquipe(Utilisateur president, UUID equipeId, EquipeRequest req) {
        Equipe e = equipeRepository.findById(equipeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipe introuvable"));
        verifieMemeClub(president, e.getClubId());
        e.setNom(req.nom());
        e.setCategorie(req.categorie());
        return toEquipeResponse(equipeRepository.save(e));
    }

    @Transactional
    public void supprimerEquipe(Utilisateur president, UUID equipeId) {
        Equipe e = equipeRepository.findById(equipeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipe introuvable"));
        verifieMemeClub(president, e.getClubId());
        equipeRepository.deleteById(equipeId);
    }

    // ── Membres ──
    @Transactional
    public MembreResponse creerMembre(Utilisateur president, MembreCreateRequest req) {
        UUID clubId = exigeClub(president);
        Role role = parseRoleMembre(req.role());

        if (utilisateurRepository.existsByEmailIgnoreCase(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email deja utilise");
        }
        if (req.equipeId() != null) {
            verifieEquipeDuClub(req.equipeId(), clubId);
        }

        Utilisateur m = new Utilisateur();
        m.setEmail(req.email());
        m.setMotDePasse(passwordEncoder.encode(req.motDePasse()));
        m.setNom(req.nom());
        m.setPrenom(req.prenom());
        m.setRole(role);
        m.setSpecialite(req.specialite());
        m.setClubId(clubId);
        m.setEquipeId(req.equipeId());

        // Lien optionnel à une fiche dès la création (compte JOUEUR uniquement).
        if (req.joueurId() != null) {
            if (role != Role.JOUEUR) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seul un compte joueur peut etre lie a une fiche");
            }
            Joueur fiche = joueurRepository.findById(req.joueurId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fiche joueur introuvable"));
            if (fiche.getEquipeId() != null) verifieEquipeDuClub(fiche.getEquipeId(), clubId);
            if (utilisateurRepository.existsByJoueurId(req.joueurId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Fiche deja reliee a un autre compte");
            }
            m.setJoueurId(req.joueurId());
            if (fiche.getEquipeId() != null) m.setEquipeId(fiche.getEquipeId());
        }

        m = utilisateurRepository.save(m);
        synchroniserAffectationPrincipale(m, clubId);
        return toMembreResponse(m);
    }

    public List<MembreResponse> listerMembres(UUID clubId) {
        return utilisateurRepository.findByClubId(clubId).stream()
                .filter(u -> ROLES_MEMBRES.contains(u.getRole()))
                .map(this::toMembreResponse)
                .toList();
    }

    @Transactional
    public MembreResponse modifierMembre(Utilisateur president, UUID membreId, MembreUpdateRequest req) {
        Utilisateur m = chargeMembreDuClub(president, membreId);
        Role ancienRole = m.getRole();
        UUID ancienneEquipe = m.getEquipeId();

        if (req.role() != null) m.setRole(parseRoleMembre(req.role()));
        if (req.specialite() != null) m.setSpecialite(req.specialite());
        if (req.equipeId() != null) {
            verifieEquipeDuClub(req.equipeId(), m.getClubId());
            m.setEquipeId(req.equipeId());
        }
        if (req.actif() != null) m.setActif(req.actif());
        m = utilisateurRepository.save(m);

        UUID clubId = m.getClubId();
        if (m.getRole() != ancienRole) {
            // Changement de rôle « principal » → on réinitialise les affectations à ce seul rôle
            // (le cumul de rôles se gère ensuite dans l'onglet « Rôles & accès »).
            synchroniserAffectationPrincipale(m, clubId);
        } else if (!java.util.Objects.equals(m.getEquipeId(), ancienneEquipe)) {
            // Le membre change d'équipe : on repointe ses affectations d'équipe sur la nouvelle.
            repointerAffectationsSurEquipe(m, clubId);
        }
        return toMembreResponse(m);
    }

    @Transactional
    public void supprimerMembre(Utilisateur president, UUID membreId) {
        Utilisateur m = chargeMembreDuClub(president, membreId);
        utilisateurRepository.deleteById(m.getId());
    }

    // ── Liaison compte JOUEUR ↔ fiche (posée a posteriori) ──
    @Transactional
    public MembreResponse lierFiche(Utilisateur acteur, UUID membreId, UUID joueurId) {
        Utilisateur m = chargeMembreDuClub(acteur, membreId);
        if (m.getRole() != Role.JOUEUR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seul un compte joueur peut etre lie a une fiche");
        }
        UUID clubId = exigeClub(acteur);
        Joueur fiche = joueurRepository.findById(joueurId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fiche joueur introuvable"));
        if (fiche.getEquipeId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fiche sans equipe");
        }
        verifieEquipeDuClub(fiche.getEquipeId(), clubId);
        if (utilisateurRepository.existsByJoueurIdAndIdNot(joueurId, membreId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Fiche deja reliee a un autre compte");
        }
        m.setJoueurId(joueurId);
        m.setEquipeId(fiche.getEquipeId()); // on aligne l'equipe du compte sur celle de la fiche
        return toMembreResponse(utilisateurRepository.save(m));
    }

    @Transactional
    public MembreResponse delierFiche(Utilisateur acteur, UUID membreId) {
        Utilisateur m = chargeMembreDuClub(acteur, membreId);
        m.setJoueurId(null);
        return toMembreResponse(utilisateurRepository.save(m));
    }

    // ── Helpers ──

    /**
     * Club sur lequel agit l'utilisateur : son club de rattachement (président,
     * entraîneur, préparateur…) ou, pour le SUPER_ADMIN sans club, le club « entré »
     * via le contexte de navigation actif.
     */
    private UUID exigeClub(Utilisateur acteur) {
        if (acteur.getClubId() != null) {
            return acteur.getClubId();
        }
        if (acteur.getRole() == Role.SUPER_ADMIN) {
            ContexteActif ctx = ContexteActifHolder.get();
            if (ctx != null && ctx.clubId() != null) {
                return ctx.clubId();
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Entrez d'abord dans un club");
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Aucun club associe");
    }

    private void verifieMemeClub(Utilisateur acteur, UUID clubId) {
        if (!exigeClub(acteur).equals(clubId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ressource hors de votre club");
        }
    }

    private void verifieEquipeDuClub(UUID equipeId, UUID clubId) {
        Equipe e = equipeRepository.findById(equipeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipe introuvable"));
        if (!e.getClubId().equals(clubId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Equipe hors de votre club");
        }
    }

    private Utilisateur chargeMembreDuClub(Utilisateur president, UUID membreId) {
        Utilisateur m = utilisateurRepository.findById(membreId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membre introuvable"));
        verifieMemeClub(president, m.getClubId());
        if (!ROLES_MEMBRES.contains(m.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Membre non gerable");
        }
        return m;
    }

    private Role parseRoleMembre(String role) {
        Role r;
        try {
            r = Role.valueOf(role);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role invalide : " + role);
        }
        if (!ROLES_MEMBRES.contains(r)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role non autorise pour un membre : " + role);
        }
        return r;
    }

    private EquipeResponse toEquipeResponse(Equipe e) {
        long nbMembres = utilisateurRepository.findByEquipeId(e.getId()).size();
        return new EquipeResponse(e.getId(), e.getNom(), e.getCategorie(), e.getClubId(), nbMembres);
    }

    private MembreResponse toMembreResponse(Utilisateur u) {
        return new MembreResponse(
                u.getId(), u.getEmail(), u.getNom(), u.getPrenom(),
                u.getRole().name(), u.getSpecialite(), u.getEquipeId(), u.getJoueurId(), u.isActif());
    }

    // ── Synchronisation du rôle « principal » (legacy) avec une affectation RBAC ──

    /**
     * Réinitialise les affectations du membre dans le club à son SEUL rôle principal (système).
     * Garantit qu'un membre fraîchement créé (ou dont on change le rôle) possède bien des
     * permissions. Le cumul de rôles supplémentaires se gère ensuite via « Rôles & accès ».
     */
    private void synchroniserAffectationPrincipale(Utilisateur membre, UUID clubId) {
        affectationRepository.deleteByUserIdAndClubId(membre.getId(), clubId);
        if (!ROLES_STAFF.contains(membre.getRole()) || membre.getEquipeId() == null) {
            return; // joueur (self-scope) ou membre sans équipe → pas d'affectation
        }
        roleRepository.findBySystemeTrueAndCode(membre.getRole().name()).ifPresent(role -> {
            AffectationRole a = new AffectationRole();
            a.setUserId(membre.getId());
            a.setClubId(clubId);
            a.setEquipeId(membre.getEquipeId());
            a.setRoleId(role.getId());
            affectationRepository.save(a);
        });
    }

    /** Repointe les affectations scopées équipe du membre sur sa nouvelle équipe (cumul préservé). */
    private void repointerAffectationsSurEquipe(Utilisateur membre, UUID clubId) {
        for (AffectationRole a : affectationRepository.findByUserIdAndClubId(membre.getId(), clubId)) {
            if (a.getEquipeId() != null) {
                a.setEquipeId(membre.getEquipeId());
                affectationRepository.save(a);
            }
        }
    }
}
