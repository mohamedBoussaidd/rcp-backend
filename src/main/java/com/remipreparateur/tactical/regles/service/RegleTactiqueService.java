package com.remipreparateur.tactical.regles.service;

import com.remipreparateur.shared.security.CurrentUserProvider;
import com.remipreparateur.shared.security.ScopeResolver;
import com.remipreparateur.tactical.regles.dto.RegleTactiqueDtos.RegleTactiqueRequest;
import com.remipreparateur.tactical.regles.dto.RegleTactiqueDtos.RegleTactiqueResponse;
import com.remipreparateur.tactical.regles.dto.RegleTactiqueDtos.RegleTactiqueResume;
import com.remipreparateur.tactical.regles.entity.RegleTactique;
import com.remipreparateur.tactical.regles.repository.RegleTactiqueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Jeux de règles du moteur tactique, portée = l'équipe active (comme le plan de jeu).
 * Un seul jeu NOUS par (équipe, système) — 409 sinon ; profils ADVERSAIRE illimités.
 * {@code reglesJson} est stocké opaque : le miroir adverse et l'interpolation sont
 * calculés côté front (source unique de la sémantique du JSON).
 */
@Service
@RequiredArgsConstructor
public class RegleTactiqueService {

    private static final String TYPE_NOUS = "NOUS";

    private final RegleTactiqueRepository regleRepository;
    private final ScopeResolver scopeResolver;
    private final CurrentUserProvider currentUser;

    public List<RegleTactiqueResume> lister(String type, String systeme) {
        UUID equipeId = scopeResolver.equipeActiveUnique();
        return regleRepository.findByEquipeIdOrderByUpdatedAtDesc(equipeId).stream()
                .filter(r -> type == null || r.getType().equals(type))
                .filter(r -> systeme == null || r.getSysteme().equals(systeme))
                .map(r -> new RegleTactiqueResume(r.getId(), r.getType(), r.getNom(), r.getSysteme(), r.getUpdatedAt()))
                .toList();
    }

    public RegleTactiqueResponse detail(UUID id) {
        return toResponse(chargeDansPerimetre(id));
    }

    public RegleTactiqueResponse creer(RegleTactiqueRequest req) {
        UUID equipeId = scopeResolver.equipeActiveUnique();
        if (TYPE_NOUS.equals(req.type())
                && regleRepository.existsByEquipeIdAndTypeAndSysteme(equipeId, TYPE_NOUS, req.systeme())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un jeu de règles existe déjà pour ce système — modifiez-le ou changez de système");
        }
        RegleTactique r = new RegleTactique();
        r.setEquipeId(equipeId);
        r.setCreePar(currentUser.current().getId());
        r.setType(req.type());
        r.setNom(req.nom());
        r.setSysteme(req.systeme());
        r.setReglesJson(req.reglesJson());
        return toResponse(regleRepository.save(r));
    }

    public RegleTactiqueResponse modifier(UUID id, RegleTactiqueRequest req) {
        RegleTactique r = chargeDansPerimetre(id);
        if (TYPE_NOUS.equals(req.type())
                && regleRepository.existsByEquipeIdAndTypeAndSystemeAndIdNot(r.getEquipeId(), TYPE_NOUS, req.systeme(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un jeu de règles existe déjà pour ce système — modifiez-le ou changez de système");
        }
        r.setType(req.type());
        r.setNom(req.nom());
        r.setSysteme(req.systeme());
        r.setReglesJson(req.reglesJson());
        r.setUpdatedAt(LocalDateTime.now());
        return toResponse(regleRepository.save(r));
    }

    public void supprimer(UUID id) {
        RegleTactique r = chargeDansPerimetre(id);
        regleRepository.delete(r);
    }

    /** Charge la règle et vérifie qu'elle appartient au périmètre (404 sinon, sans révéler). */
    private RegleTactique chargeDansPerimetre(UUID id) {
        RegleTactique r = regleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Jeu de règles introuvable"));
        scopeResolver.verifieAcces(r.getEquipeId());
        return r;
    }

    private RegleTactiqueResponse toResponse(RegleTactique r) {
        return new RegleTactiqueResponse(r.getId(), r.getType(), r.getNom(), r.getSysteme(),
                r.getReglesJson(), r.getUpdatedAt());
    }
}
