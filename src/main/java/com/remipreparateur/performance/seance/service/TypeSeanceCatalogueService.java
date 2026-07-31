package com.remipreparateur.performance.seance.service;

import com.remipreparateur.performance.seance.dto.TypeSeanceDtos.TypeSeanceResponse;
import com.remipreparateur.performance.seance.entity.TypeSeance;
import com.remipreparateur.performance.seance.entity.TypeSeanceCible;
import com.remipreparateur.performance.seance.repository.TypeSeanceCibleRepository;
import com.remipreparateur.performance.seance.repository.TypeSeanceRepository;
import com.remipreparateur.shared.security.ScopeResolver;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Catalogue des types de séance <b>résolu pour le club actif</b> : cibles physiques ET couleur.
 *
 * <p>Point d'attention : {@code type_seance} est un catalogue GLOBAL. Les réponses d'API qui
 * sérialisent directement l'entité (ex. {@code /api/seances}, qui renvoie {@code Seance} avec sa
 * relation {@code typeSeance}) exposent donc la couleur PAR DÉFAUT de la plateforme, jamais la
 * surcharge du club. C'est ce service — et lui seul — qui applique la résolution
 * {@code cible.couleur ?? type.couleur} ; le front doit donc colorer depuis ce catalogue et non
 * depuis la couleur embarquée dans une séance.
 */
@Service
public class TypeSeanceCatalogueService {

    private final TypeSeanceRepository typeSeanceRepository;
    private final TypeSeanceCibleRepository cibleRepository;
    private final ScopeResolver scopeResolver;

    public TypeSeanceCatalogueService(TypeSeanceRepository typeSeanceRepository,
                                      TypeSeanceCibleRepository cibleRepository,
                                      ScopeResolver scopeResolver) {
        this.typeSeanceRepository = typeSeanceRepository;
        this.cibleRepository = cibleRepository;
        this.scopeResolver = scopeResolver;
    }

    /** Tous les types, enrichis des réglages du club actif. */
    public List<TypeSeanceResponse> catalogue() {
        Map<UUID, TypeSeanceCible> cibles = ciblesDuClub();
        return typeSeanceRepository.findAll().stream()
                .map(t -> toResponse(t, cibles.get(t.getId())))
                .toList();
    }

    private Map<UUID, TypeSeanceCible> ciblesDuClub() {
        try {
            UUID club = scopeResolver.clubActif();
            if (club == null) return Map.of();
            return cibleRepository.findByClubId(club).stream()
                    .collect(Collectors.toMap(TypeSeanceCible::getTypeSeanceId, Function.identity()));
        } catch (RuntimeException e) {
            return Map.of();   // super-admin sans contexte : couleurs et cibles par défaut
        }
    }

    /** Réponse d'un type pour le club actif (cible éventuellement absente). */
    public TypeSeanceResponse toResponse(TypeSeance t, TypeSeanceCible c) {
        // Un type de profil MUSCULATION (ou SANS_CHARGE_EXTERNE) n'a pas de cibles kilométriques :
        // les renvoyer ferait réapparaître des mètres là où le lot F vient de les supprimer.
        boolean gps = t.attendGps();
        // Couleur affichée : celle du club si elle existe, sinon le défaut du catalogue (V94).
        String couleur = (c != null && c.getCouleur() != null) ? c.getCouleur() : t.getCouleur();
        return new TypeSeanceResponse(
                t.getId(), t.getCode(), t.getLibelle(), t.getJourSemaine(),
                t.getIntensiteTheorique(), t.getObjectifPrincipal(), t.getDureeTheoriqueMin(),
                t.getProfil(), couleur,
                c == null || !gps ? null : c.getObjectifDistanceM(),
                c == null || !gps ? null : c.getObjectifDistanceHauteIntensiteM(),
                c == null ? null : c.getObjectifIntensite());
    }
}
