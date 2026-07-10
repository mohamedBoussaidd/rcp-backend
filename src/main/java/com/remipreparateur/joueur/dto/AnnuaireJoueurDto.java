package com.remipreparateur.joueur.dto;

import java.util.List;
import java.util.UUID;

/**
 * Ligne d'annuaire club : une personne (fiche joueur) et ses équipes d'appartenance dérivées de
 * l'effectif de la saison EN_COURS. {@code assigne=false} → fiche « pool » (non assignée).
 */
public record AnnuaireJoueurDto(
        UUID joueurId,
        String nom,
        String prenom,
        String poste,
        List<EquipeRef> equipes,
        boolean assigne
) {
    public record EquipeRef(UUID id, String nom) {}
}
