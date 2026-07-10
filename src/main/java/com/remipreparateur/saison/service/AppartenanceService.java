package com.remipreparateur.saison.service;

import com.remipreparateur.saison.repository.EffectifSaisonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Dérive « l'appartenance d'équipe » d'une personne depuis {@code effectif_saison} — source de
 * vérité multi-équipe qui remplace le cache legacy {@code joueur.equipe_id} (Phase 4).
 *
 * <ul>
 *   <li>{@link #equipesDe(UUID)} : équipes courantes (effectif de la saison EN_COURS) — pour le
 *       scoping d'accès et la diffusion des notifications à TOUTES les équipes de la personne.</li>
 *   <li>{@link #equipePrincipale(UUID)} : une seule équipe, pour « tamponner » une donnée créée
 *       (blessure, wellness, entretien…). = l'effectif EN_COURS le plus récemment rejoint ; à défaut
 *       (hors-saison), la dernière équipe connue toutes saisons ; {@code null} si jamais en effectif.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AppartenanceService {

    private final EffectifSaisonRepository effectifRepository;

    /** Équipes courantes de la personne (effectif EN_COURS). Vide = pool / hors-saison. */
    public List<UUID> equipesDe(UUID joueurId) {
        return effectifRepository.findEquipesActivesOrdonnees(joueurId).stream().distinct().toList();
    }

    /** Équipe « principale » pour tamponner une donnée. {@code null} si la personne n'a aucun effectif. */
    public UUID equipePrincipale(UUID joueurId) {
        List<UUID> actives = effectifRepository.findEquipesActivesOrdonnees(joueurId);
        if (!actives.isEmpty()) return actives.get(0);
        List<UUID> toutes = effectifRepository.findEquipesOrdonnees(joueurId);
        return toutes.isEmpty() ? null : toutes.get(0);
    }
}
