package com.remipreparateur.performance.importation.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remipreparateur.auth.entity.Role;
import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.performance.importation.dto.MappingColonne;
import com.remipreparateur.performance.importation.dto.MetriqueImport;
import com.remipreparateur.performance.importation.entity.ProfilImportGps;
import com.remipreparateur.performance.importation.repository.ProfilImportGpsRepository;
import com.remipreparateur.shared.security.ContexteActif;
import com.remipreparateur.shared.security.ContexteActifHolder;
import com.remipreparateur.shared.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * Métriques GPS réellement alimentées par le club + seuils réels de ses zones de vitesse,
 * dérivés de ses profils d'import (affichage « niveau 1 » : libellés de zones dynamiques,
 * masquage des colonnes jamais importées). Club sans profil → tout actif, seuils par défaut
 * (comportement historique). Lecture seule, tout utilisateur authentifié (comme le thème).
 */
@RestController
@RequestMapping("/api/gps/metriques-actives")
@RequiredArgsConstructor
public class MetriquesGpsController {

    /** Métriques d'affichage (les colonnes techniques IDENTITE / DATE_SEANCE n'en font pas partie). */
    private static final List<MetriqueImport> AFFICHABLES = List.of(
            MetriqueImport.DUREE, MetriqueImport.DISTANCE_TOTALE,
            MetriqueImport.DISTANCE_Z15, MetriqueImport.DISTANCE_Z19,
            MetriqueImport.DISTANCE_Z24, MetriqueImport.DISTANCE_Z28,
            MetriqueImport.NB_SPRINTS, MetriqueImport.VITESSE_MAX,
            MetriqueImport.NB_ACCELERATIONS, MetriqueImport.NB_FREINAGES,
            MetriqueImport.RATIO_DISTANCE_MIN);

    private final ProfilImportGpsRepository profilRepository;
    private final CurrentUserProvider currentUser;
    private final ObjectMapper objectMapper;

    public record MetriquesActivesResponse(boolean profil, List<String> metriquesActives, Map<String, Double> seuils) {}

    @GetMapping
    public MetriquesActivesResponse metriquesActives() {
        Map<String, Double> seuils = new LinkedHashMap<>();
        seuils.put("z15", 15.0);
        seuils.put("z19", 19.0);
        seuils.put("z24", 24.0);
        seuils.put("z28", 28.0);

        UUID clubId = clubCourant();
        List<ProfilImportGps> profils = clubId == null ? List.of() : profilRepository.findByClubId(clubId);
        if (profils.isEmpty()) {
            return new MetriquesActivesResponse(false,
                    AFFICHABLES.stream().map(Enum::name).toList(), seuils);
        }

        // Union des métriques mappées sur l'ensemble des profils du club (multi-équipes possible).
        Set<MetriqueImport> actives = EnumSet.noneOf(MetriqueImport.class);
        for (ProfilImportGps profil : profils) {
            for (MappingColonne m : parseMappings(profil.getMappings())) {
                if (m.getMetrique() == null) continue;
                actives.add(m.getMetrique());
                if (m.getSeuilReel() == null) continue;
                switch (m.getMetrique()) {
                    case DISTANCE_Z15 -> seuils.put("z15", m.getSeuilReel());
                    case DISTANCE_Z19 -> seuils.put("z19", m.getSeuilReel());
                    case DISTANCE_Z24 -> seuils.put("z24", m.getSeuilReel());
                    case DISTANCE_Z28 -> seuils.put("z28", m.getSeuilReel());
                    default -> { }
                }
            }
        }
        List<String> noms = AFFICHABLES.stream().filter(actives::contains).map(Enum::name).toList();
        return new MetriquesActivesResponse(true, noms, seuils);
    }

    private UUID clubCourant() {
        Utilisateur u = currentUser.current();
        UUID clubId = u.getClubId();
        if (clubId == null && u.getRole() == Role.SUPER_ADMIN) {
            ContexteActif ctx = ContexteActifHolder.get();
            clubId = ctx != null ? ctx.clubId() : null;
        }
        return clubId;
    }

    private List<MappingColonne> parseMappings(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<MappingColonne>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
