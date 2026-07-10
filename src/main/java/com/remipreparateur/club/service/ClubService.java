package com.remipreparateur.club.service;

import com.remipreparateur.club.dto.ClubDtos.ClubCreateRequest;
import com.remipreparateur.club.dto.ClubDtos.ClubResponse;
import com.remipreparateur.club.dto.ClubDtos.EquipeApercu;
import com.remipreparateur.club.entity.Club;
import com.remipreparateur.club.entity.Equipe;
import com.remipreparateur.auth.entity.Role;
import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.auth.rbac.AffectationRole;
import com.remipreparateur.auth.rbac.AffectationRoleRepository;
import com.remipreparateur.auth.rbac.RoleApplicatifRepository;
import com.remipreparateur.club.repository.ClubRepository;
import com.remipreparateur.club.repository.EquipeRepository;
import com.remipreparateur.documentadmin.service.ReferentielDocumentAdminSeeder;
import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.auth.repository.UtilisateurRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class ClubService {

    private final ClubRepository clubRepository;
    private final EquipeRepository equipeRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final JoueurRepository joueurRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleApplicatifRepository roleRepository;
    private final AffectationRoleRepository affectationRepository;
    private final ReferentielDocumentAdminSeeder referentielSeeder;

    public ClubService(ClubRepository clubRepository,
                       EquipeRepository equipeRepository,
                       UtilisateurRepository utilisateurRepository,
                       JoueurRepository joueurRepository,
                       PasswordEncoder passwordEncoder,
                       RoleApplicatifRepository roleRepository,
                       AffectationRoleRepository affectationRepository,
                       ReferentielDocumentAdminSeeder referentielSeeder) {
        this.clubRepository = clubRepository;
        this.equipeRepository = equipeRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.joueurRepository = joueurRepository;
        this.passwordEncoder = passwordEncoder;
        this.roleRepository = roleRepository;
        this.affectationRepository = affectationRepository;
        this.referentielSeeder = referentielSeeder;
    }

    /** Cree le club ET son president (en une transaction). */
    @Transactional
    public ClubResponse creerClubAvecPresident(ClubCreateRequest req) {
        if (utilisateurRepository.existsByEmailIgnoreCase(req.president().email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email deja utilise");
        }

        Club club = new Club();
        club.setNom(req.nom());
        club.setLogo(req.logo());
        club = clubRepository.save(club);

        // Référentiel documentaire par défaut (catégories d'âge + types de documents JOUEUR/STAFF).
        // Les seeds V47/V49 ne visent que les clubs préexistants : pour un club neuf, c'est ici le
        // seul endroit qui pose le référentiel (même contenu). Reste dans la transaction courante.
        referentielSeeder.seederReferentielParDefaut(club.getId());

        Utilisateur president = new Utilisateur();
        president.setEmail(req.president().email());
        president.setMotDePasse(passwordEncoder.encode(req.president().motDePasse()));
        president.setNom(req.president().nom());
        president.setPrenom(req.president().prenom());
        president.setRole(Role.PRESIDENT);
        president.setClubId(club.getId());
        president = utilisateurRepository.save(president);

        // Conformité documentaire (Phase 3) : le président obtient une fiche `personne` au niveau
        // club (licence dirigeant, honorabilité...). Fiche non assignée → hors listes joueurs (Phase 2).
        Joueur fiche = new Joueur();
        fiche.setNom(president.getNom() != null && !president.getNom().isBlank() ? president.getNom() : "—");
        fiche.setPrenom(president.getPrenom() != null && !president.getPrenom().isBlank() ? president.getPrenom() : "—");
        fiche.setClubId(club.getId());
        fiche.setStatut("actif");
        fiche = joueurRepository.save(fiche);
        president.setJoueurId(fiche.getId());
        president = utilisateurRepository.save(president);

        club.setPresidentId(president.getId());
        club = clubRepository.save(club);

        // Affectation RBAC club-wide vers le rôle système PRESIDENT : sans elle, le président
        // n'aurait AUCUNE permission (PermissionResolver ne special-case que le super-admin, cf.
        // le bug corrigé en V48). equipe_id NULL = tout le club.
        creerAffectationPresident(president.getId(), club.getId());

        return toResponse(club);
    }

    /** Pose l'affectation club-wide du président vers le rôle système PRESIDENT (idempotent en pratique). */
    private void creerAffectationPresident(UUID presidentId, UUID clubId) {
        roleRepository.findBySystemeTrueAndCode(Role.PRESIDENT.name()).ifPresent(role -> {
            AffectationRole a = new AffectationRole();
            a.setUserId(presidentId);
            a.setClubId(clubId);
            a.setEquipeId(null);
            a.setRoleId(role.getId());
            affectationRepository.save(a);
        });
    }

    public List<ClubResponse> listerClubs() {
        return clubRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public ClubResponse modifier(UUID id, com.remipreparateur.club.dto.ClubDtos.ClubUpdateRequest req) {
        Club club = clubRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Club introuvable"));
        club.setNom(req.nom());
        club.setLogo(req.logo());
        return toResponse(clubRepository.save(club));
    }

    @Transactional
    public void supprimerClub(UUID id) {
        if (!clubRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Club introuvable");
        }
        clubRepository.deleteById(id);
    }

    /** Active ou archive un club (sans supprimer ses données). */
    @Transactional
    public ClubResponse definirActif(UUID id, boolean actif) {
        Club club = clubRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Club introuvable"));
        club.setActif(actif);
        return toResponse(clubRepository.save(club));
    }

    /** Équipes d'un club, pour le sélecteur de contexte du super-admin. */
    public List<EquipeApercu> listerEquipes(UUID clubId) {
        if (!clubRepository.existsById(clubId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Club introuvable");
        }
        return equipeRepository.findByClubId(clubId).stream()
                .map(e -> new EquipeApercu(e.getId(), e.getNom(), e.getCategorie()))
                .toList();
    }

    private ClubResponse toResponse(Club club) {
        Utilisateur p = club.getPresidentId() != null
                ? utilisateurRepository.findById(club.getPresidentId()).orElse(null)
                : null;
        List<UUID> equipeIds = equipeRepository.findByClubId(club.getId())
                .stream().map(Equipe::getId).toList();
        long nbJoueurs = equipeIds.isEmpty() ? 0 : joueurRepository.countByEquipeIdIn(equipeIds);
        return new ClubResponse(
                club.getId(), club.getNom(), club.getLogo(), club.getDateCreation(),
                p != null ? p.getId() : null,
                p != null ? p.getEmail() : null,
                p != null ? p.getNom() : null,
                p != null ? p.getPrenom() : null,
                equipeIds.size(), nbJoueurs, club.isActif());
    }
}
