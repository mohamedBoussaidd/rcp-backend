package com.remipreparateur.badge.service;

import com.remipreparateur.badge.entity.BadgeDefinition;
import com.remipreparateur.badge.entity.BadgePortee;
import com.remipreparateur.badge.entity.EntiteBadge;
import com.remipreparateur.badge.entity.TypeEntite;
import com.remipreparateur.badge.repository.BadgeDefinitionRepository;
import com.remipreparateur.badge.repository.EntiteBadgeRepository;
import com.remipreparateur.badge.service.BadgeService.BadgeDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Assignation et lecture des tags (badges plateforme) posés sur des entités. L'assignation est
 * réservée au super-admin (contrôlée au niveau du controller) ; la lecture sert à l'affichage des
 * badges sur les cards, visible par tout utilisateur.
 */
@Service
public class EntiteBadgeService {

    private final EntiteBadgeRepository liens;
    private final BadgeDefinitionRepository definitions;

    public EntiteBadgeService(EntiteBadgeRepository liens, BadgeDefinitionRepository definitions) {
        this.liens = liens;
        this.definitions = definitions;
    }

    /** Tags actifs posés sur une entité, dans l'ordre d'assignation. */
    @Transactional(readOnly = true)
    public List<BadgeDto> badgesDe(TypeEntite type, UUID id) {
        return versDtos(liens.findAllByTypeEntiteAndEntiteId(type, id));
    }

    /** Tous les tags d'un type d'entité, groupés par entité (pour l'affichage en liste/cards). */
    @Transactional(readOnly = true)
    public Map<UUID, List<BadgeDto>> badgesParType(TypeEntite type) {
        List<EntiteBadge> tous = liens.findAllByTypeEntite(type);
        Map<UUID, BadgeDefinition> defs = defsActifs(tous.stream().map(EntiteBadge::getBadgeId).toList());
        Map<UUID, List<BadgeDto>> out = new LinkedHashMap<>();
        for (EntiteBadge l : tous) {
            BadgeDefinition d = defs.get(l.getBadgeId());
            if (d != null) out.computeIfAbsent(l.getEntiteId(), k -> new ArrayList<>()).add(BadgeService.toDto(d));
        }
        return out;
    }

    /** Remplace l'ensemble des tags d'une entité (diff : n'ajoute/retire que le nécessaire). */
    @Transactional
    public List<BadgeDto> assigner(TypeEntite type, UUID id, List<UUID> badgeIds) {
        Set<UUID> cible = new LinkedHashSet<>(badgeIds == null ? List.of() : badgeIds);
        // On ne garde que des tags plateforme existants (un badge système n'est jamais assigné).
        Map<UUID, BadgeDefinition> valides = definitions.findAllById(cible).stream()
                .filter(d -> d.getPortee() == BadgePortee.PLATEFORME)
                .collect(Collectors.toMap(BadgeDefinition::getId, Function.identity()));
        cible.retainAll(valides.keySet());

        List<EntiteBadge> existants = liens.findAllByTypeEntiteAndEntiteId(type, id);
        Set<UUID> dejaLa = existants.stream().map(EntiteBadge::getBadgeId).collect(Collectors.toSet());

        // Retire ce qui n'est plus voulu, ajoute ce qui manque (aucun ré-insert → pas de conflit unique).
        liens.deleteAll(existants.stream().filter(e -> !cible.contains(e.getBadgeId())).toList());
        for (UUID bid : cible) {
            if (dejaLa.contains(bid)) continue;
            EntiteBadge e = new EntiteBadge();
            e.setTypeEntite(type);
            e.setEntiteId(id);
            e.setBadgeId(bid);
            liens.save(e);
        }
        return badgesDe(type, id);
    }

    private List<BadgeDto> versDtos(List<EntiteBadge> ls) {
        Map<UUID, BadgeDefinition> defs = defsActifs(ls.stream().map(EntiteBadge::getBadgeId).toList());
        return ls.stream()
                .map(l -> defs.get(l.getBadgeId()))
                .filter(java.util.Objects::nonNull)
                .map(BadgeService::toDto)
                .toList();
    }

    private Map<UUID, BadgeDefinition> defsActifs(List<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return definitions.findAllById(ids).stream()
                .filter(BadgeDefinition::isActif)
                .collect(Collectors.toMap(BadgeDefinition::getId, Function.identity()));
    }
}
