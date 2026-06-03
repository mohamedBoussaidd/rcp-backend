package com.remipreparateur.service;

import com.remipreparateur.dto.ClubDtos.ClubCreateRequest;
import com.remipreparateur.dto.ClubDtos.ClubResponse;
import com.remipreparateur.entity.Club;
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

import java.util.List;
import java.util.UUID;

@Service
public class ClubService {

    private final ClubRepository clubRepository;
    private final EquipeRepository equipeRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;

    public ClubService(ClubRepository clubRepository,
                       EquipeRepository equipeRepository,
                       UtilisateurRepository utilisateurRepository,
                       PasswordEncoder passwordEncoder) {
        this.clubRepository = clubRepository;
        this.equipeRepository = equipeRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.passwordEncoder = passwordEncoder;
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

        Utilisateur president = new Utilisateur();
        president.setEmail(req.president().email());
        president.setMotDePasse(passwordEncoder.encode(req.president().motDePasse()));
        president.setNom(req.president().nom());
        president.setPrenom(req.president().prenom());
        president.setRole(Role.PRESIDENT);
        president.setClubId(club.getId());
        president = utilisateurRepository.save(president);

        club.setPresidentId(president.getId());
        club = clubRepository.save(club);

        return toResponse(club);
    }

    public List<ClubResponse> listerClubs() {
        return clubRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public void supprimerClub(UUID id) {
        if (!clubRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Club introuvable");
        }
        clubRepository.deleteById(id);
    }

    private ClubResponse toResponse(Club club) {
        Utilisateur p = club.getPresidentId() != null
                ? utilisateurRepository.findById(club.getPresidentId()).orElse(null)
                : null;
        long nbEquipes = equipeRepository.countByClubId(club.getId());
        return new ClubResponse(
                club.getId(), club.getNom(), club.getLogo(), club.getDateCreation(),
                p != null ? p.getId() : null,
                p != null ? p.getEmail() : null,
                p != null ? p.getNom() : null,
                p != null ? p.getPrenom() : null,
                nbEquipes);
    }
}
