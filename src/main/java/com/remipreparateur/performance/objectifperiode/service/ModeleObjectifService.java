package com.remipreparateur.performance.objectifperiode.service;

import com.remipreparateur.auth.rbac.FeatureModule;
import com.remipreparateur.auth.rbac.PermissionResolver;
import com.remipreparateur.club.pack.ClubModulesService;
import com.remipreparateur.performance.objectifperiode.dto.ObjectifPeriodeDtos.*;
import com.remipreparateur.performance.objectifperiode.entity.ModeleObjectif;
import com.remipreparateur.performance.objectifperiode.entity.ModeleObjectifPhase;
import com.remipreparateur.performance.objectifperiode.entity.ModeleObjectifPhaseValeur;
import com.remipreparateur.performance.objectifperiode.repository.ModeleObjectifPhaseRepository;
import com.remipreparateur.performance.objectifperiode.repository.ModeleObjectifPhaseValeurRepository;
import com.remipreparateur.performance.objectifperiode.repository.ModeleObjectifRepository;
import com.remipreparateur.performance.objectifperiode.repository.ObjectifPeriodeRepository;
import com.remipreparateur.performance.referentiel.MetriqueCharge;
import com.remipreparateur.performance.referentiel.PrioriteMetrique;
import com.remipreparateur.shared.security.CurrentUserProvider;
import org.springframework.http.HttpStatus;
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
 * Modèles d'objectif réutilisables d'un club : la FORME d'une période, sans ses kilomètres.
 *
 * <p>Rien ici ne connaît de distance : un modèle ne porte que des phases et des pourcentages de
 * la cible du référentiel. C'est ce qui permet au même « Prépa — progression classique » de
 * servir un club N1 et un club régional sans être dupliqué ni retapé.
 *
 * <p>Même sémantique que les autres modèles de l'application (séance-modèle, modèle de semaine) :
 * on instancie par COPIE, et corriger le modèle après coup ne rattrape pas les instances déjà
 * posées sur des périodes.
 */
@Service
public class ModeleObjectifService {

    private final ModeleObjectifRepository modeleRepository;
    private final ModeleObjectifPhaseRepository phaseRepository;
    private final ModeleObjectifPhaseValeurRepository valeurRepository;
    private final ObjectifPeriodeRepository objectifPeriodeRepository;
    private final PermissionResolver permissionResolver;
    private final ClubModulesService clubModulesService;
    private final CurrentUserProvider currentUser;

    public ModeleObjectifService(ModeleObjectifRepository modeleRepository,
                                 ModeleObjectifPhaseRepository phaseRepository,
                                 ModeleObjectifPhaseValeurRepository valeurRepository,
                                 ObjectifPeriodeRepository objectifPeriodeRepository,
                                 PermissionResolver permissionResolver,
                                 ClubModulesService clubModulesService,
                                 CurrentUserProvider currentUser) {
        this.modeleRepository = modeleRepository;
        this.phaseRepository = phaseRepository;
        this.valeurRepository = valeurRepository;
        this.objectifPeriodeRepository = objectifPeriodeRepository;
        this.permissionResolver = permissionResolver;
        this.clubModulesService = clubModulesService;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<ModeleResume> lister(String typePeriode) {
        exigeModule();
        UUID clubId = clubActif();
        List<ModeleObjectif> modeles = (typePeriode == null || typePeriode.isBlank())
                ? modeleRepository.findByClubIdOrderByNomAsc(clubId)
                : modeleRepository.findByClubIdAndTypePeriodeOrderByNomAsc(clubId, typePeriode);
        return modeles.stream().map(this::toResume).toList();
    }

    @Transactional(readOnly = true)
    public ModeleDetail detail(UUID id) {
        exigeModule();
        ModeleObjectif m = exigeModele(id);
        return new ModeleDetail(toResume(m), phasesDe(m.getId()));
    }

    /** Crée ou remplace intégralement un modèle (phases comprises). */
    @Transactional
    public ModeleDetail enregistrer(UUID id, ModeleRequest req) {
        exigeModule();
        UUID clubId = clubActif();
        ModeleObjectif m = id == null ? new ModeleObjectif() : exigeModele(id);
        if (req.nom() == null || req.nom().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le nom est obligatoire.");
        }
        if (req.typePeriode() == null || req.typePeriode().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le type de période est obligatoire.");
        }
        m.setClubId(clubId);
        m.setNom(req.nom().trim());
        m.setTypePeriode(req.typePeriode().trim().toUpperCase());
        if (m.getCreePar() == null) m.setCreePar(currentUser.current().getId());
        m.setUpdatedAt(LocalDateTime.now());
        m = modeleRepository.save(m);

        remplacerPhases(m.getId(), req.phases() == null ? List.of() : req.phases());
        return detail(m.getId());
    }

    /**
     * Supprime un modèle. Les instances déjà posées sur des périodes ne sont PAS touchées : elles
     * portent leurs propres valeurs, figées à la génération. Elles perdent seulement le lien de
     * traçabilité vers le modèle d'origine.
     */
    @Transactional
    public void supprimer(UUID id) {
        exigeModule();
        ModeleObjectif m = exigeModele(id);
        List<UUID> phaseIds = phaseRepository.findByModeleIdOrderByOrdreAsc(id).stream()
                .map(ModeleObjectifPhase::getId).toList();
        if (!phaseIds.isEmpty()) valeurRepository.deleteByPhaseIdIn(phaseIds);
        phaseRepository.deleteByModeleId(id);
        modeleRepository.delete(m);
    }

    /** Phases d'un modèle avec leurs valeurs, triées — utilisé aussi par l'instanciation. */
    @Transactional(readOnly = true)
    public List<PhaseDto> phasesDe(UUID modeleId) {
        List<ModeleObjectifPhase> phases = phaseRepository.findByModeleIdOrderByOrdreAsc(modeleId);
        if (phases.isEmpty()) return List.of();
        List<UUID> ids = phases.stream().map(ModeleObjectifPhase::getId).toList();
        Map<UUID, List<ModeleObjectifPhaseValeur>> parPhase = valeurRepository.findByPhaseIdIn(ids)
                .stream().collect(Collectors.groupingBy(ModeleObjectifPhaseValeur::getPhaseId));
        List<PhaseDto> res = new ArrayList<>();
        for (ModeleObjectifPhase p : phases) {
            List<PhaseValeurDto> valeurs = parPhase.getOrDefault(p.getId(), List.of()).stream()
                    .sorted(java.util.Comparator.comparingInt(v -> {
                        MetriqueCharge m = MetriqueCharge.parCode(v.getMetrique());
                        return m == null ? 99 : m.getOrdre();
                    }))
                    .map(v -> new PhaseValeurDto(v.getMetrique(), v.getPctDebut(), v.getPctFin(),
                            v.getPriorite()))
                    .toList();
            res.add(new PhaseDto(p.getId(), p.getOrdre(), p.getNom(), p.getPoidsDuree(), valeurs));
        }
        return res;
    }

    // ── Helpers ──

    private void remplacerPhases(UUID modeleId, List<PhaseDto> phases) {
        List<UUID> anciens = phaseRepository.findByModeleIdOrderByOrdreAsc(modeleId).stream()
                .map(ModeleObjectifPhase::getId).toList();
        if (!anciens.isEmpty()) valeurRepository.deleteByPhaseIdIn(anciens);
        phaseRepository.deleteByModeleId(modeleId);
        phaseRepository.flush();

        short ordre = 0;
        for (PhaseDto p : phases) {
            if (p.nom() == null || p.nom().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chaque phase doit avoir un nom.");
            }
            ModeleObjectifPhase phase = new ModeleObjectifPhase();
            phase.setModeleId(modeleId);
            phase.setOrdre(ordre++);
            phase.setNom(p.nom().trim());
            // Un poids nul ou négatif rendrait la répartition indéterminée : on plancher à 1.
            phase.setPoidsDuree((short) Math.max(1, p.poidsDuree()));
            phase = phaseRepository.save(phase);

            List<ModeleObjectifPhaseValeur> valeurs = new ArrayList<>();
            for (PhaseValeurDto v : (p.valeurs() == null ? List.<PhaseValeurDto>of() : p.valeurs())) {
                if (MetriqueCharge.parCode(v.metrique()) == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Métrique inconnue : " + v.metrique());
                }
                if (v.pctDebut() == null && v.pctFin() == null) continue;
                ModeleObjectifPhaseValeur ligne = new ModeleObjectifPhaseValeur();
                ligne.setPhaseId(phase.getId());
                ligne.setMetrique(v.metrique());
                int debut = v.pctDebut() != null ? v.pctDebut() : v.pctFin();
                int fin   = v.pctFin()   != null ? v.pctFin()   : v.pctDebut();
                ligne.setPctDebut(debut);
                ligne.setPctFin(fin);
                ligne.setPriorite(PrioriteMetrique.parNom(v.priorite()).name());
                valeurs.add(ligne);
            }
            valeurRepository.saveAll(valeurs);
        }
    }

    private ModeleResume toResume(ModeleObjectif m) {
        return new ModeleResume(m.getId(), m.getNom(), m.getTypePeriode(),
                phaseRepository.findByModeleIdOrderByOrdreAsc(m.getId()).size(),
                objectifPeriodeRepository.countByModeleId(m.getId()), m.getUpdatedAt());
    }

    private ModeleObjectif exigeModele(UUID id) {
        UUID clubId = clubActif();
        ModeleObjectif m = modeleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Modèle introuvable"));
        if (!clubId.equals(m.getClubId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Modèle introuvable");
        }
        return m;
    }

    private UUID clubActif() {
        UUID clubId = permissionResolver.clubActif(currentUser.current());
        if (clubId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Aucun club actif");
        }
        return clubId;
    }

    private void exigeModule() {
        UUID clubId = permissionResolver.clubActif(currentUser.current());
        if (clubId == null) return;
        if (!clubModulesService.modulesActifs(clubId).contains(FeatureModule.OBJECTIFS_PERFORMANCE.getCode())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Le module « " + FeatureModule.OBJECTIFS_PERFORMANCE.getLibelle()
                            + " » n'est pas activé pour votre club.");
        }
    }
}
