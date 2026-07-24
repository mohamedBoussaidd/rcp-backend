package com.remipreparateur.badge.service;

import com.remipreparateur.badge.entity.BadgeDefinition;
import com.remipreparateur.badge.entity.BadgeMode;
import com.remipreparateur.badge.entity.BadgePortee;
import com.remipreparateur.badge.entity.BadgeTon;
import com.remipreparateur.badge.repository.BadgeDefinitionRepository;
import com.remipreparateur.badge.repository.BadgePaletteTonRepository;
import com.remipreparateur.badge.service.BadgeService.BadgeDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Gestion des badges par le super-admin : édition des badges (label/icône/ton/couleur, activation)
 * et de la palette des 6 tons. La CRUD des tags plateforme (portée PLATEFORME) s'ajoutera en P3.
 */
@Service
public class BadgeAdminService {

    private final BadgeDefinitionRepository definitions;
    private final BadgePaletteTonRepository palette;

    public BadgeAdminService(BadgeDefinitionRepository definitions, BadgePaletteTonRepository palette) {
        this.definitions = definitions;
        this.palette = palette;
    }

    /** Tous les badges (système + tags), pour l'écran de gestion. */
    @Transactional(readOnly = true)
    public List<BadgeDto> listerBadges() {
        return definitions.findAllByOrderByOrdreAsc().stream().map(BadgeService::toDto).toList();
    }

    /** Édite un badge existant (par sa clé). Ne change ni la clé ni la portée. */
    @Transactional
    public BadgeDto majBadge(String cle, BadgeUpdateDto d) {
        BadgeDefinition b = definitions.findByCle(cle)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Badge inconnu : " + cle));
        if (d.label() != null && !d.label().isBlank()) b.setLabel(d.label().trim());
        b.setIcone(vide(d.icone()) ? null : d.icone().trim());
        BadgeTon ton = BadgeService.parseTon(d.ton());
        if (ton != null) b.setTon(ton);
        BadgeMode mode = parseMode(d.mode());
        if (mode != null) b.setMode(mode);
        b.setCouleurBg(vide(d.couleurBg()) ? null : d.couleurBg().trim());
        b.setCouleurFg(vide(d.couleurFg()) ? null : d.couleurFg().trim());
        b.setTooltip(vide(d.tooltip()) ? null : d.tooltip().trim());
        if (d.actif() != null) b.setActif(d.actif());
        if (d.ordre() != null) b.setOrdre(d.ordre());
        return BadgeService.toDto(definitions.save(b));
    }

    /** Crée un tag (badge de portée PLATEFORME) : couleur explicite fixe, assignable ensuite. */
    @Transactional
    public BadgeDto creerTag(TagCreateDto d) {
        if (d.label() == null || d.label().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Libellé requis");
        }
        BadgeDefinition b = new BadgeDefinition();
        b.setCle(genererCle(d.label()));
        b.setLabel(d.label().trim());
        b.setIcone(vide(d.icone()) ? null : d.icone().trim());
        BadgeTon ton = BadgeService.parseTon(d.ton());
        b.setTon(ton != null ? ton : BadgeTon.NEUTRAL);
        b.setMode(BadgeMode.INLINE);
        b.setPortee(BadgePortee.PLATEFORME);
        b.setCouleurBg(vide(d.couleurBg()) ? null : d.couleurBg().trim());
        b.setCouleurFg(vide(d.couleurFg()) ? null : d.couleurFg().trim());
        b.setTooltip(vide(d.tooltip()) ? null : d.tooltip().trim());
        b.setActif(true);
        b.setOrdre(1000);
        return BadgeService.toDto(definitions.save(b));
    }

    /** Supprime un tag plateforme (les assignations partent en cascade). Interdit sur un badge système. */
    @Transactional
    public void supprimerTag(String cle) {
        BadgeDefinition b = definitions.findByCle(cle)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Badge inconnu : " + cle));
        if (b.getPortee() != BadgePortee.PLATEFORME) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un badge système ne peut pas être supprimé");
        }
        definitions.delete(b);
    }

    /** Clé unique dérivée du libellé (les tags ne sont pas référencés par le code, mais la clé est unique). */
    private String genererCle(String label) {
        String base = "tag-" + label.toLowerCase().trim()
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (base.equals("tag-")) base = "tag";
        String cle = base;
        int n = 2;
        while (definitions.findByCle(cle).isPresent()) {
            cle = base + "-" + n++;
        }
        return cle;
    }

    /** Palette des 6 tons (avec le marqueur de personnalisation). */
    @Transactional(readOnly = true)
    public List<PaletteTonDto> palette() {
        return palette.findAll().stream()
                .map(t -> new PaletteTonDto(t.getTon().name(), t.getLibelle(),
                        t.getCouleurBg(), t.getCouleurFg(), t.isPersonnalise()))
                .toList();
    }

    /** Met à jour un ton de la palette. {@code personnalise=false} le remet au défaut theme-aware. */
    @Transactional
    public List<PaletteTonDto> majPalette(List<PaletteTonDto> valeurs) {
        for (PaletteTonDto v : valeurs) {
            BadgeTon ton = BadgeService.parseTon(v.ton());
            if (ton == null) continue;
            palette.findById(ton).ifPresent(t -> {
                if (v.libelle() != null && !v.libelle().isBlank()) t.setLibelle(v.libelle().trim());
                if (!vide(v.couleurBg())) t.setCouleurBg(v.couleurBg().trim());
                if (!vide(v.couleurFg())) t.setCouleurFg(v.couleurFg().trim());
                t.setPersonnalise(v.personnalise());
                t.setUpdatedAt(LocalDateTime.now());
                palette.save(t);
            });
        }
        return palette();
    }

    private static boolean vide(String s) {
        return s == null || s.isBlank();
    }

    private static BadgeMode parseMode(String s) {
        try { return s == null ? null : BadgeMode.valueOf(s.toUpperCase()); }
        catch (Exception e) { return null; }
    }

    public record BadgeUpdateDto(String label, String icone, String ton, String mode,
                                 String couleurBg, String couleurFg, String tooltip,
                                 Boolean actif, Integer ordre) {}

    public record TagCreateDto(String label, String icone, String ton,
                               String couleurBg, String couleurFg, String tooltip) {}

    public record PaletteTonDto(String ton, String libelle, String couleurBg, String couleurFg,
                                boolean personnalise) {}
}
