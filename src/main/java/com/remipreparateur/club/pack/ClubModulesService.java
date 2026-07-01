package com.remipreparateur.club.pack;

import com.remipreparateur.auth.rbac.FeatureModule;
import com.remipreparateur.club.entity.Club;
import com.remipreparateur.club.pack.PackDtos.*;
import com.remipreparateur.club.pack.entity.ClubModule;
import com.remipreparateur.club.pack.entity.ClubModuleId;
import com.remipreparateur.club.pack.entity.Pack;
import com.remipreparateur.club.pack.repository.ClubModuleRepository;
import com.remipreparateur.club.pack.repository.PackRepository;
import com.remipreparateur.club.repository.ClubRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Résolution et administration des modules actifs d'un club.
 *
 * <p><b>Résolution live</b> : les modules effectifs = {@code modules(pack) ∪ surcharges}, socle
 * toujours inclus. Aucun cache/matérialisation → éditer un pack se propage immédiatement aux clubs
 * qui l'utilisent, tout en laissant les surcharges d'un club gagner (add-on / retrait explicite).
 *
 * <p><b>Fail-open</b> : un club sans pack (ou introuvable) obtient tous les modules → on ne casse
 * jamais un club non encore configuré. Après la migration V42, tous les clubs existants ont un pack.
 */
@Service
public class ClubModulesService {

    private final PackRepository packs;
    private final ClubModuleRepository clubModules;
    private final ClubRepository clubs;

    public ClubModulesService(PackRepository packs, ClubModuleRepository clubModules, ClubRepository clubs) {
        this.packs = packs;
        this.clubModules = clubModules;
        this.clubs = clubs;
    }

    // ── Résolution (utilisée par PermissionResolver + /api/me/modules) ──────────

    /** Codes des modules actifs pour un club (socle + pack + surcharges). */
    @Transactional(readOnly = true)
    public Set<String> modulesActifs(UUID clubId) {
        Set<String> res = new LinkedHashSet<>(FeatureModule.socleCodes());
        if (clubId == null) {
            res.addAll(FeatureModule.activableCodes());
            return res;
        }
        Club club = clubs.findById(clubId).orElse(null);
        if (club == null) {
            res.addAll(FeatureModule.activableCodes());
            return res;
        }
        res.addAll(modulesDuPack(club.getPackCode()));
        for (ClubModule cm : clubModules.findByClubId(clubId)) {
            if (cm.isActif()) res.add(cm.getModuleCode());
            else res.remove(cm.getModuleCode());
        }
        res.addAll(FeatureModule.socleCodes()); // le socle n'est jamais retirable
        return res;
    }

    private Set<String> modulesDuPack(String packCode) {
        if (packCode == null) {
            return new HashSet<>(FeatureModule.activableCodes()); // fail-open
        }
        return packs.findById(packCode)
                .map(p -> new HashSet<>(p.getModules()))
                .orElseGet(() -> new HashSet<>(FeatureModule.activableCodes()));
    }

    // ── Catalogue des modules ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ModuleDto> catalogueModules() {
        return Arrays.stream(FeatureModule.values())
                .sorted(Comparator.comparingInt(FeatureModule::getOrdre))
                .map(m -> new ModuleDto(m.getCode(), m.getLibelle(), m.getDescription(), m.isSocle(), m.getOrdre()))
                .toList();
    }

    // ── CRUD des packs ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PackDto> listerPacks() {
        return packs.findAllByOrderByOrdreAsc().stream().map(this::toDto).toList();
    }

    @Transactional
    public PackDto creerPack(PackUpsertRequest req) {
        Pack p = new Pack();
        p.setCode(genererCode(req.libelle()));
        p.setPredefini(false);
        appliquer(p, req);
        return toDto(packs.save(p));
    }

    @Transactional
    public PackDto majPack(String code, PackUpsertRequest req) {
        Pack p = packs.findById(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pack introuvable"));
        appliquer(p, req);
        return toDto(packs.save(p));
    }

    @Transactional
    public void supprimerPack(String code) {
        Pack p = packs.findById(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pack introuvable"));
        if (p.isPredefini()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Un pack prédéfini ne peut pas être supprimé.");
        }
        if (clubs.countByPackCode(code) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ce pack est attribué à des clubs. Réattribuez-les à un autre pack avant de le supprimer.");
        }
        packs.delete(p);
    }

    /** Applique un upsert : garde uniquement des modules activables connus, vérifie la cohérence. */
    private void appliquer(Pack p, PackUpsertRequest req) {
        if (req.libelle() == null || req.libelle().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le libellé du pack est obligatoire.");
        }
        p.setLibelle(req.libelle().trim());
        p.setDescription(req.description());
        p.setPrixMensuel(req.prixMensuel());
        p.setOrdre(req.ordre() != null ? req.ordre() : 0);
        p.setActif(req.actif() == null || req.actif());

        Set<String> demandes = req.modules() == null ? Set.of() : req.modules();
        Set<String> activables = FeatureModule.activableCodes();
        Set<String> valides = demandes.stream().filter(activables::contains)
                .collect(Collectors.toCollection(HashSet::new));

        // Un pack doit être cohérent en lui-même (ex. Prépa physique ⇒ Wellness ou GPS).
        Set<String> pourValidation = new HashSet<>(valides);
        pourValidation.addAll(FeatureModule.socleCodes());
        validerCoherence(pourValidation);

        p.setModules(valides);
    }

    // ── Abonnement d'un club (état + affectation) ─────────────────────────────

    @Transactional(readOnly = true)
    public ClubAbonnementDto abonnement(UUID clubId) {
        Club club = clubs.findById(clubId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Club introuvable"));
        Set<String> packMods = modulesDuPack(club.getPackCode());
        Map<String, Boolean> overrides = clubModules.findByClubId(clubId).stream()
                .collect(Collectors.toMap(ClubModule::getModuleCode, ClubModule::isActif));

        List<ClubModuleEtatDto> etats = Arrays.stream(FeatureModule.values())
                .sorted(Comparator.comparingInt(FeatureModule::getOrdre))
                .map(m -> {
                    String c = m.getCode();
                    boolean actif;
                    String source;
                    if (m.isSocle()) {
                        actif = true;
                        source = "SOCLE";
                    } else if (overrides.containsKey(c)) {
                        actif = overrides.get(c);
                        source = actif ? "MANUEL_ON" : "MANUEL_OFF";
                    } else if (packMods.contains(c)) {
                        actif = true;
                        source = "PACK";
                    } else {
                        actif = false;
                        source = "INACTIF";
                    }
                    return new ClubModuleEtatDto(c, m.getLibelle(), m.getDescription(), m.isSocle(), actif, source);
                })
                .toList();
        return new ClubAbonnementDto(clubId, club.getPackCode(), etats);
    }

    @Transactional
    public ClubAbonnementDto assignerPack(UUID clubId, String packCode) {
        Club club = clubs.findById(clubId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Club introuvable"));
        if (packCode != null && !packs.existsById(packCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pack inconnu : " + packCode);
        }
        club.setPackCode(packCode);
        clubs.save(club);
        // Le pack + les surcharges existantes doivent rester cohérents (rollback si non).
        validerCoherence(modulesActifs(clubId));
        return abonnement(clubId);
    }

    @Transactional
    public ClubAbonnementDto definirModule(UUID clubId, String moduleCode, boolean actif) {
        Club club = clubs.findById(clubId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Club introuvable"));
        FeatureModule m = FeatureModule.parCode(moduleCode);
        if (m == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Module inconnu : " + moduleCode);
        }
        if (m.isSocle()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le module « " + m.getLibelle() + " » fait partie du socle et ne peut pas être désactivé.");
        }

        ClubModuleId id = new ClubModuleId(clubId, moduleCode);
        boolean packDefaut = modulesDuPack(club.getPackCode()).contains(moduleCode);
        if (actif == packDefaut) {
            // Ré-aligné sur le pack : on retire la surcharge (le module suit de nouveau le pack).
            clubModules.findById(id).ifPresent(clubModules::delete);
        } else {
            ClubModule cm = clubModules.findById(id).orElseGet(() -> {
                ClubModule x = new ClubModule();
                x.setClubId(clubId);
                x.setModuleCode(moduleCode);
                return x;
            });
            cm.setActif(actif);
            cm.setMajLe(LocalDateTime.now());
            clubModules.save(cm);
        }

        validerCoherence(modulesActifs(clubId)); // rollback si incohérent (force-source)
        return abonnement(clubId);
    }

    // ── Cohérence : un module actif doit avoir ses dépendances (force-source) ──

    private void validerCoherence(Set<String> modules) {
        for (FeatureModule m : FeatureModule.values()) {
            Set<FeatureModule> deps = m.requiertAuMoinsUn();
            if (modules.contains(m.getCode()) && !deps.isEmpty()
                    && deps.stream().noneMatch(d -> modules.contains(d.getCode()))) {
                String libs = deps.stream().map(FeatureModule::getLibelle).collect(Collectors.joining(" ou "));
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Le module « " + m.getLibelle() + " » nécessite au moins : " + libs + ".");
            }
        }
    }

    // ── Utilitaires ───────────────────────────────────────────────────────────

    private String genererCode(String libelle) {
        String base = (libelle == null ? "pack" : libelle).toLowerCase()
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (base.isBlank()) base = "pack";
        String code = base;
        int i = 1;
        while (packs.existsById(code)) {
            code = base + "-" + (++i);
        }
        return code;
    }

    private PackDto toDto(Pack p) {
        List<String> mods = p.getModules().stream().sorted().toList();
        return new PackDto(p.getCode(), p.getLibelle(), p.getDescription(), p.getPrixMensuel(),
                p.getOrdre(), p.isActif(), p.isPredefini(), mods);
    }
}
