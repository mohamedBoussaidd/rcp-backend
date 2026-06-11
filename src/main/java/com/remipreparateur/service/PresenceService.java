package com.remipreparateur.service;

import com.remipreparateur.dto.PresenceDtos.*;
import com.remipreparateur.entity.Joueur;
import com.remipreparateur.entity.Presence;
import com.remipreparateur.entity.Presence.StatutPresence;
import com.remipreparateur.entity.Seance;
import com.remipreparateur.repository.JoueurRepository;
import com.remipreparateur.repository.PresenceRepository;
import com.remipreparateur.repository.SeanceRepository;
import com.remipreparateur.security.ScopeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PresenceService {

    private final PresenceRepository presenceRepository;
    private final SeanceRepository seanceRepository;
    private final JoueurRepository joueurRepository;
    private final ScopeResolver scopeResolver;

    /** Retourne la feuille de présence d'une séance : l'effectif complet avec leur statut. */
    @Transactional(readOnly = true)
    public FeuillePresence getFeuille(UUID seanceId) {
        Seance seance = seanceRepository.findById(seanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Séance introuvable"));
        scopeResolver.verifieAcces(seance.getEquipeId());

        // Effectif de l'équipe
        List<Joueur> joueurs = joueurRepository.findByEquipeIdIn(List.of(seance.getEquipeId()));

        // Présences déjà enregistrées pour cette séance
        Map<UUID, Presence> existantes = presenceRepository.findBySeanceId(seanceId)
                .stream().collect(Collectors.toMap(Presence::getJoueurId, Function.identity()));

        List<LignePresence> lignes = joueurs.stream()
                .sorted((a, b) -> a.getNom().compareToIgnoreCase(b.getNom()))
                .map(j -> {
                    Presence p = existantes.get(j.getId());
                    return new LignePresence(
                            j.getId(),
                            j.getPrenom(),
                            j.getNom(),
                            j.getPostePrincipal(),
                            p != null ? p.getStatut() : null,   // null = non renseigné
                            p != null ? p.getNote() : null);
                })
                .collect(Collectors.toList());

        return new FeuillePresence(seanceId, lignes);
    }

    /** Sauvegarde la feuille complète (upsert : crée ou met à jour chaque ligne). */
    @Transactional
    public FeuillePresence saveFeuille(UUID seanceId, SaveFeuillePresence req) {
        Seance seance = seanceRepository.findById(seanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Séance introuvable"));
        scopeResolver.verifieAcces(seance.getEquipeId());

        for (SaveFeuillePresence.SaveLigne ligne : req.lignes()) {
            Presence p = presenceRepository
                    .findBySeanceIdAndJoueurId(seanceId, ligne.joueurId())
                    .orElseGet(() -> {
                        Presence n = new Presence();
                        n.setSeanceId(seanceId);
                        n.setJoueurId(ligne.joueurId());
                        return n;
                    });
            p.setStatut(ligne.statut());
            p.setNote(ligne.note());
            presenceRepository.save(p);
        }
        return getFeuille(seanceId);
    }

    /** Sauvegarde/met à jour la présence d'un seul joueur. */
    @Transactional
    public LignePresence saveUne(UUID seanceId, UUID joueurId, SavePresence req) {
        Seance seance = seanceRepository.findById(seanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Séance introuvable"));
        scopeResolver.verifieAcces(seance.getEquipeId());

        Joueur joueur = joueurRepository.findById(joueurId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Joueur introuvable"));

        Presence p = presenceRepository.findBySeanceIdAndJoueurId(seanceId, joueurId)
                .orElseGet(() -> {
                    Presence n = new Presence();
                    n.setSeanceId(seanceId);
                    n.setJoueurId(joueurId);
                    return n;
                });
        p.setStatut(req.statut() != null ? req.statut() : StatutPresence.PRESENT);
        p.setNote(req.note());
        presenceRepository.save(p);

        return new LignePresence(joueurId, joueur.getPrenom(), joueur.getNom(),
                joueur.getPostePrincipal(), p.getStatut(), p.getNote());
    }

    /** Historique de présence d'un joueur (toutes ses séances). */
    @Transactional(readOnly = true)
    public List<Presence> getHistoriqueJoueur(UUID joueurId) {
        return presenceRepository.findByJoueurId(joueurId);
    }
}
