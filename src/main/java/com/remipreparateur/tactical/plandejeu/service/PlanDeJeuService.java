package com.remipreparateur.tactical.plandejeu.service;

import com.remipreparateur.tactical.plandejeu.dto.PlanDeJeuDtos.PlanDeJeuResponse;
import com.remipreparateur.tactical.plandejeu.dto.PlanDeJeuDtos.ReordonnerRequest;
import com.remipreparateur.tactical.plandejeu.dto.PlanDeJeuDtos.SectionCreateRequest;
import com.remipreparateur.tactical.plandejeu.dto.PlanDeJeuDtos.SectionResponse;
import com.remipreparateur.tactical.plandejeu.dto.PlanDeJeuDtos.SectionUpdateRequest;
import com.remipreparateur.tactical.plandejeu.entity.PlanDeJeu;
import com.remipreparateur.tactical.plandejeu.entity.SectionPlan;
import com.remipreparateur.auth.entity.Role;
import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.auth.rbac.PermissionResolver;
import com.remipreparateur.tactical.plandejeu.repository.PlanDeJeuRepository;
import com.remipreparateur.tactical.plandejeu.repository.SectionPlanRepository;
import com.remipreparateur.shared.security.CurrentUserProvider;
import com.remipreparateur.shared.security.ScopeResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Plan de jeu (« document d'identité équipe »), unique et vivant par équipe.
 * Lecture : staff ; écriture : entraineur / president / super-admin (cf. SecurityConfig).
 * Le document et ses 6 sections standard sont créés à la volée au premier accès.
 */
@Service
public class PlanDeJeuService {

    /** Squelette pré-rempli d'une identité de jeu (titres seulement, à compléter). */
    private static final List<String> SECTIONS_STANDARD = List.of(
            "Système & animations de base",
            "Phase offensive",
            "Phase défensive",
            "Transitions",
            "Coups de pied arrêtés offensifs",
            "Coups de pied arrêtés défensifs");

    private final PlanDeJeuRepository planRepository;
    private final SectionPlanRepository sectionRepository;
    private final CurrentUserProvider currentUser;
    private final ScopeResolver scopeResolver;
    private final PermissionResolver permissionResolver;

    public PlanDeJeuService(PlanDeJeuRepository planRepository,
                            SectionPlanRepository sectionRepository,
                            CurrentUserProvider currentUser,
                            ScopeResolver scopeResolver,
                            PermissionResolver permissionResolver) {
        this.planRepository = planRepository;
        this.sectionRepository = sectionRepository;
        this.currentUser = currentUser;
        this.scopeResolver = scopeResolver;
        this.permissionResolver = permissionResolver;
    }

    @Transactional
    public PlanDeJeuResponse getPlan() {
        Utilisateur u = currentUser.current();
        PlanDeJeu plan = planPourEquipeActive(u);
        return toResponse(plan, u);
    }

    @Transactional
    public SectionResponse modifierSection(UUID sectionId, SectionUpdateRequest req) {
        SectionPlan s = chargerSectionDansPerimetre(sectionId);
        s.setTitre(req.titre());
        s.setTexte(req.texte());
        s.setSchemaJson(req.schemaJson());
        s.setApercu(req.apercu());
        s.setUpdatedAt(LocalDateTime.now());
        return toResponse(sectionRepository.save(s));
    }

    @Transactional
    public SectionResponse ajouterSection(SectionCreateRequest req) {
        Utilisateur u = currentUser.current();
        PlanDeJeu plan = planPourEquipeActive(u);
        List<SectionPlan> sections = sectionRepository.findByPlanDeJeuIdOrderByOrdreAsc(plan.getId());
        int ordre = sections.isEmpty() ? 0 : sections.get(sections.size() - 1).getOrdre() + 1;
        SectionPlan s = new SectionPlan();
        s.setPlanDeJeuId(plan.getId());
        s.setTitre(req.titre());
        s.setTexte(req.texte());
        s.setOrdre(ordre);
        s.setCreePar(u.getId());
        return toResponse(sectionRepository.save(s));
    }

    @Transactional
    public void supprimerSection(UUID sectionId) {
        SectionPlan s = chargerSectionDansPerimetre(sectionId);
        sectionRepository.delete(s);
    }

    @Transactional
    public PlanDeJeuResponse reordonner(ReordonnerRequest req) {
        Utilisateur u = currentUser.current();
        PlanDeJeu plan = planPourEquipeActive(u);
        List<SectionPlan> sections = sectionRepository.findByPlanDeJeuIdOrderByOrdreAsc(plan.getId());
        for (SectionPlan s : sections) {
            int idx = req.ordreIds().indexOf(s.getId());
            if (idx < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Section absente de l'ordre fourni");
            }
            s.setOrdre(idx);
        }
        sectionRepository.saveAll(sections);
        return toResponse(plan, u);
    }

    /** Plan de l'équipe active, créé (avec ses 6 sections standard) au premier accès. */
    private PlanDeJeu planPourEquipeActive(Utilisateur u) {
        UUID equipeId = scopeResolver.equipeActiveUnique();
        return planRepository.findByEquipeId(equipeId)
                .orElseGet(() -> creerPlanInitial(equipeId, u));
    }

    private PlanDeJeu creerPlanInitial(UUID equipeId, Utilisateur u) {
        PlanDeJeu plan = new PlanDeJeu();
        plan.setEquipeId(equipeId);
        plan = planRepository.save(plan);
        int ordre = 0;
        for (String titre : SECTIONS_STANDARD) {
            SectionPlan s = new SectionPlan();
            s.setPlanDeJeuId(plan.getId());
            s.setTitre(titre);
            s.setOrdre(ordre++);
            s.setCreePar(u.getId());
            sectionRepository.save(s);
        }
        return plan;
    }

    /** Charge une section en vérifiant que son équipe est dans le périmètre de l'utilisateur. */
    private SectionPlan chargerSectionDansPerimetre(UUID sectionId) {
        SectionPlan s = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Section introuvable"));
        PlanDeJeu plan = planRepository.findById(s.getPlanDeJeuId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan de jeu introuvable"));
        scopeResolver.verifieAcces(plan.getEquipeId());
        return s;
    }

    private PlanDeJeuResponse toResponse(PlanDeJeu plan, Utilisateur u) {
        List<SectionResponse> sections = sectionRepository.findByPlanDeJeuIdOrderByOrdreAsc(plan.getId())
                .stream().map(this::toResponse).toList();
        return new PlanDeJeuResponse(plan.getId(), plan.getEquipeId(), peutModifier(u), sections);
    }

    private SectionResponse toResponse(SectionPlan s) {
        return new SectionResponse(s.getId(), s.getTitre(), s.getTexte(), s.getSchemaJson(),
                s.getApercu(), s.getOrdre(), s.getUpdatedAt());
    }

    /** Droit d'édition (indicatif pour l'UI) ; l'écriture est aussi gardée par SecurityConfig. */
    private boolean peutModifier(Utilisateur u) {
        return u.getRole() == Role.SUPER_ADMIN || permissionResolver.permissionsPour(u).contains("plandejeu:write");
    }
}
