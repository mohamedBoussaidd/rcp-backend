package com.remipreparateur.badge.service;

import com.remipreparateur.badge.entity.BadgeCouleurClub;
import com.remipreparateur.badge.entity.BadgeDefinition;
import com.remipreparateur.badge.entity.BadgeTon;
import com.remipreparateur.badge.repository.BadgeCouleurClubRepository;
import com.remipreparateur.badge.repository.BadgeDefinitionRepository;
import com.remipreparateur.badge.repository.BadgePaletteTonRepository;
import com.remipreparateur.shared.security.ScopeResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Registre des badges consommé par le front (composant {@code <app-badge>}), et surcharge de
 * couleur par club. La registry ne renvoie parmi les tons QUE ceux réellement personnalisés
 * (plateforme) ou surchargés par le club courant : le front n'injecte que ceux-là et laisse les
 * défauts theme-aware de {@code :root} pour le reste.
 */
@Service
public class BadgeService {

    private final BadgeDefinitionRepository definitions;
    private final BadgePaletteTonRepository palette;
    private final BadgeCouleurClubRepository couleursClub;
    private final ScopeResolver scopeResolver;

    public BadgeService(BadgeDefinitionRepository definitions,
                        BadgePaletteTonRepository palette,
                        BadgeCouleurClubRepository couleursClub,
                        ScopeResolver scopeResolver) {
        this.definitions = definitions;
        this.palette = palette;
        this.couleursClub = couleursClub;
        this.scopeResolver = scopeResolver;
    }

    /** Registre pour l'hydratation du front : badges actifs + tons personnalisés (effectifs club). */
    @Transactional(readOnly = true)
    public RegistryDto registry() {
        List<BadgeDto> badges = definitions.findAllByActifTrueOrderByOrdreAsc().stream()
                .map(BadgeService::toDto)
                .toList();

        Map<BadgeTon, BadgeCouleurClub> clubMap = couleursClubCourant().stream()
                .collect(Collectors.toMap(BadgeCouleurClub::getTon, c -> c));

        List<TonDto> tons = new ArrayList<>();
        palette.findAll().forEach(t -> {
            BadgeCouleurClub cc = clubMap.get(t.getTon());
            if (cc != null) {
                tons.add(new TonDto(t.getTon().name(), t.getLibelle(), cc.getCouleurBg(), cc.getCouleurFg()));
            } else if (t.isPersonnalise()) {
                tons.add(new TonDto(t.getTon().name(), t.getLibelle(), t.getCouleurBg(), t.getCouleurFg()));
            }
        });
        return new RegistryDto(badges, tons);
    }

    // ── Surcharge de couleur par club (6 tons) ──

    /** Surcharges de couleur du club courant (pour l'écran d'apparence du club). */
    @Transactional(readOnly = true)
    public List<CouleurTonDto> couleursDuClub() {
        return couleursClub.findAllByClubId(scopeResolver.clubActif()).stream()
                .map(c -> new CouleurTonDto(c.getTon().name(), c.getCouleurBg(), c.getCouleurFg()))
                .toList();
    }

    /**
     * Remplace les surcharges de couleur du club courant. Une entrée par ton à surcharger ;
     * les tons absents de la liste sont remis au défaut (ligne supprimée).
     */
    @Transactional
    public List<CouleurTonDto> majCouleursDuClub(List<CouleurTonDto> valeurs) {
        UUID clubId = scopeResolver.clubActif();
        couleursClub.deleteAll(couleursClub.findAllByClubId(clubId));
        for (CouleurTonDto v : valeurs) {
            BadgeTon ton = parseTon(v.ton());
            if (ton == null || v.couleurBg() == null || v.couleurFg() == null) continue;
            BadgeCouleurClub c = new BadgeCouleurClub();
            c.setClubId(clubId);
            c.setTon(ton);
            c.setCouleurBg(v.couleurBg());
            c.setCouleurFg(v.couleurFg());
            c.setUpdatedAt(LocalDateTime.now());
            couleursClub.save(c);
        }
        return couleursDuClub();
    }

    // ── Helpers ──

    /** Surcharges du club courant, ou vide si aucun club déterminable (super-admin hors contexte). */
    private List<BadgeCouleurClub> couleursClubCourant() {
        try {
            return couleursClub.findAllByClubId(scopeResolver.clubActif());
        } catch (ResponseStatusException e) {
            return List.of();
        }
    }

    static BadgeTon parseTon(String s) {
        try { return BadgeTon.valueOf(String.valueOf(s).toUpperCase()); }
        catch (Exception e) { return null; }
    }

    static BadgeDto toDto(BadgeDefinition b) {
        return new BadgeDto(
                b.getId(), b.getCle(), b.getLabel(), b.getIcone(), b.getTon().name(),
                b.getMode().name(), b.getPortee().name(), b.getCouleurBg(), b.getCouleurFg(),
                b.getTooltip(), b.getOrdre(), b.isActif());
    }

    // ── DTOs ──

    public record BadgeDto(UUID id, String cle, String label, String icone, String ton,
                           String mode, String portee, String couleurBg, String couleurFg,
                           String tooltip, int ordre, boolean actif) {}

    public record TonDto(String ton, String libelle, String couleurBg, String couleurFg) {}

    public record CouleurTonDto(String ton, String couleurBg, String couleurFg) {}

    public record RegistryDto(List<BadgeDto> badges, List<TonDto> tons) {}
}
