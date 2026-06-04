package com.remipreparateur.service;

import com.remipreparateur.dto.GestionDtos.*;
import com.remipreparateur.entity.Club;
import com.remipreparateur.entity.Equipe;
import com.remipreparateur.entity.Role;
import com.remipreparateur.entity.Utilisateur;
import com.remipreparateur.repository.ClubRepository;
import com.remipreparateur.repository.EquipeRepository;
import com.remipreparateur.repository.UtilisateurRepository;
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

    private final EquipeRepository equipeRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ClubRepository clubRepository;
    private final PasswordEncoder passwordEncoder;

    public GestionClubService(EquipeRepository equipeRepository,
                              UtilisateurRepository utilisateurRepository,
                              ClubRepository clubRepository,
                              PasswordEncoder passwordEncoder) {
        this.equipeRepository = equipeRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.clubRepository = clubRepository;
        this.passwordEncoder = passwordEncoder;
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
        m.setJoueurId(req.joueurId());
        m = utilisateurRepository.save(m);
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
        if (req.role() != null) m.setRole(parseRoleMembre(req.role()));
        if (req.specialite() != null) m.setSpecialite(req.specialite());
        if (req.equipeId() != null) {
            verifieEquipeDuClub(req.equipeId(), m.getClubId());
            m.setEquipeId(req.equipeId());
        }
        if (req.actif() != null) m.setActif(req.actif());
        return toMembreResponse(utilisateurRepository.save(m));
    }

    @Transactional
    public void supprimerMembre(Utilisateur president, UUID membreId) {
        Utilisateur m = chargeMembreDuClub(president, membreId);
        utilisateurRepository.deleteById(m.getId());
    }

    // ── Helpers ──
    private UUID exigeClub(Utilisateur president) {
        if (president.getClubId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Aucun club associe");
        }
        return president.getClubId();
    }

    private void verifieMemeClub(Utilisateur president, UUID clubId) {
        if (!exigeClub(president).equals(clubId)) {
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
}
