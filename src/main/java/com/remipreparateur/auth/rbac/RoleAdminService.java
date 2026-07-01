package com.remipreparateur.auth.rbac;

import com.remipreparateur.auth.entity.Role;
import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.auth.rbac.RbacDtos.*;
import com.remipreparateur.auth.repository.UtilisateurRepository;
import com.remipreparateur.club.entity.Equipe;
import com.remipreparateur.club.repository.EquipeRepository;
import com.remipreparateur.shared.security.ContexteActif;
import com.remipreparateur.shared.security.ContexteActifHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Administration des rôles & accès d'un club (gérée par le président / super-admin).
 * Permet de créer des rôles CUSTOM (paquets de permissions) et d'affecter un ou plusieurs
 * rôles à un membre du staff, scopés à son équipe. Garde-fou anti-escalade : on ne peut
 * accorder que des permissions que l'on détient soi-même.
 */
@Service
public class RoleAdminService {

    /** Rôles (legacy) éligibles à une affectation RBAC : staff uniquement (pas joueur/président). */
    private static final Set<Role> STAFF_AFFECTABLE =
            EnumSet.of(Role.ENTRAINEUR, Role.PREPARATEUR, Role.MEDICAL, Role.ADMINISTRATIF);

    private final RoleApplicatifRepository roleRepo;
    private final RolePermissionRepository rolePermRepo;
    private final AffectationRoleRepository affectationRepo;
    private final UtilisateurRepository utilisateurRepo;
    private final EquipeRepository equipeRepo;
    private final PermissionResolver permissionResolver;

    public RoleAdminService(RoleApplicatifRepository roleRepo,
                            RolePermissionRepository rolePermRepo,
                            AffectationRoleRepository affectationRepo,
                            UtilisateurRepository utilisateurRepo,
                            EquipeRepository equipeRepo,
                            PermissionResolver permissionResolver) {
        this.roleRepo = roleRepo;
        this.rolePermRepo = rolePermRepo;
        this.affectationRepo = affectationRepo;
        this.utilisateurRepo = utilisateurRepo;
        this.equipeRepo = equipeRepo;
        this.permissionResolver = permissionResolver;
    }

    // ─────────────────────────── Catalogue ───────────────────────────

    public List<PermissionDto> catalogue() {
        return java.util.Arrays.stream(Permission.values())
                .map(p -> new PermissionDto(p.getCode(), p.getModule(), p.getLibelle()))
                .toList();
    }

    // ─────────────────────────── Rôles ───────────────────────────

    /** Rôles disponibles dans le club actif : système (communs) + globaux custom + custom du club. */
    public List<RoleDto> listerRoles(Utilisateur acteur) {
        UUID clubId = exigeClub(acteur);
        List<RoleApplicatif> roles = new ArrayList<>(roleRepo.findBySystemeTrue());
        roles.addAll(roleRepo.findBySystemeFalseAndClubIdIsNull());  // globaux custom (gérés par le super-admin)
        roles.addAll(roleRepo.findByClubId(clubId));
        return roles.stream().map(this::toRoleDto).toList();
    }

    @Transactional
    public RoleDto creerRole(Utilisateur acteur, RoleUpsertRequest req) {
        UUID clubId = exigeClub(acteur);
        List<String> perms = valideEtFiltre(req.permissions());
        verifieNonEscalade(acteur, perms);

        RoleApplicatif r = new RoleApplicatif();
        r.setClubId(clubId);
        r.setCode(genererCode(clubId, req.libelle()));
        r.setLibelle(req.libelle().trim());
        r.setSysteme(false);
        r = roleRepo.save(r);
        remplacePermissions(r.getId(), perms);
        return toRoleDto(r);
    }

    @Transactional
    public RoleDto majRole(Utilisateur acteur, UUID roleId, RoleUpsertRequest req) {
        RoleApplicatif r = chargeRoleCustomDuClub(acteur, roleId);
        List<String> perms = valideEtFiltre(req.permissions());
        verifieNonEscalade(acteur, perms);
        r.setLibelle(req.libelle().trim());
        roleRepo.save(r);
        remplacePermissions(r.getId(), perms);
        return toRoleDto(r);
    }

    @Transactional
    public void supprimerRole(Utilisateur acteur, UUID roleId) {
        RoleApplicatif r = chargeRoleCustomDuClub(acteur, roleId);
        if (affectationRepo.countByRoleId(r.getId()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Rôle attribué à des membres — retirez-le d'abord");
        }
        rolePermRepo.deleteByRoleId(r.getId());
        roleRepo.delete(r);
    }

    // ─────────────────────────── Rôles GLOBAUX (super-admin) ───────────────────────────
    // Hors de tout club : éditer les permissions des rôles PRÉDÉFINIS et gérer des rôles
    // globaux custom réutilisables par tous les clubs. Réservé au SUPER_ADMIN (bypass « dieu »),
    // donc pas d'anti-escalade ni d'exigence de club. Les présidents ne peuvent que les ATTRIBUER.

    /** Tous les rôles globaux (sans club) : prédéfinis système + globaux custom. */
    public List<RoleDto> listerGlobaux() {
        List<RoleApplicatif> roles = new ArrayList<>(roleRepo.findBySystemeTrue());
        roles.addAll(roleRepo.findBySystemeFalseAndClubIdIsNull());
        return roles.stream().map(this::toRoleDto).toList();
    }

    @Transactional
    public RoleDto creerGlobal(RoleUpsertRequest req) {
        List<String> perms = valideEtFiltre(req.permissions());
        RoleApplicatif r = new RoleApplicatif();
        r.setClubId(null);
        r.setCode(genererCodeGlobal(req.libelle()));
        r.setLibelle(req.libelle().trim());
        r.setSysteme(false);
        r = roleRepo.save(r);
        remplacePermissions(r.getId(), perms);
        return toRoleDto(r);
    }

    @Transactional
    public RoleDto majGlobal(UUID roleId, RoleUpsertRequest req) {
        RoleApplicatif r = chargeRoleGlobal(roleId);
        List<String> perms = valideEtFiltre(req.permissions());
        r.setLibelle(req.libelle().trim());   // code immuable : ancre du mapping legacy utilisateur.role
        roleRepo.save(r);
        remplacePermissions(r.getId(), perms);
        return toRoleDto(r);
    }

    @Transactional
    public void supprimerGlobal(UUID roleId) {
        RoleApplicatif r = chargeRoleGlobal(roleId);
        if (r.isSysteme()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Un rôle prédéfini ne peut pas être supprimé");
        }
        if (affectationRepo.countByRoleId(r.getId()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Rôle attribué à des membres — retirez-le d'abord");
        }
        rolePermRepo.deleteByRoleId(r.getId());
        roleRepo.delete(r);
    }

    /** Charge un rôle GLOBAL (club_id NULL) : prédéfini ou global custom. */
    private RoleApplicatif chargeRoleGlobal(UUID roleId) {
        RoleApplicatif r = roleRepo.findById(roleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rôle introuvable"));
        if (r.getClubId() != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Rôle non global (custom d'un club)");
        }
        return r;
    }

    /** Code unique parmi les rôles globaux (préfixe G_), distinct des codes système et des C_ de club. */
    private String genererCodeGlobal(String libelle) {
        String base = libelle.toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
        base = "G_" + (base.isBlank() ? "ROLE" : base);
        if (base.length() > 46) base = base.substring(0, 46);
        Set<String> existants = new java.util.HashSet<>();
        roleRepo.findBySystemeTrue().forEach(r -> existants.add(r.getCode()));
        roleRepo.findBySystemeFalseAndClubIdIsNull().forEach(r -> existants.add(r.getCode()));
        String code = base;
        int i = 1;
        while (existants.contains(code)) {
            code = base + "_" + (++i);
        }
        return code;
    }

    // ─────────────────────────── Affectations ───────────────────────────

    /** Rôles actuellement attribués à un membre du club. */
    public List<AffectationDto> listerAffectations(Utilisateur acteur, UUID membreId) {
        UUID clubId = exigeClub(acteur);
        chargeStaffDuClub(membreId, clubId);
        return affectationRepo.findByUserIdAndClubId(membreId, clubId).stream()
                .map(this::toAffectationDto).toList();
    }

    /** Remplace l'ensemble des rôles d'un membre (dans le club actif). */
    @Transactional
    public List<AffectationDto> definirRoles(Utilisateur acteur, UUID membreId, DefinirRolesRequest req) {
        UUID clubId = exigeClub(acteur);
        Utilisateur membre = chargeStaffDuClub(membreId, clubId);

        affectationRepo.deleteByUserIdAndClubId(membreId, clubId);
        List<Role> rolesSystemeAssignes = new ArrayList<>();
        for (AffectationItem item : req.roles()) {
            RoleApplicatif role = chargeRoleDisponible(item.roleId(), clubId);
            verifieNonEscalade(acteur, permissionsDuRole(role.getId()));

            UUID equipeId = item.equipeId() != null ? item.equipeId() : membre.getEquipeId();
            if (equipeId != null) {
                verifieEquipeDuClub(equipeId, clubId);
            }
            AffectationRole a = new AffectationRole();
            a.setUserId(membreId);
            a.setClubId(clubId);
            a.setEquipeId(equipeId);
            a.setRoleId(role.getId());
            affectationRepo.save(a);

            if (role.isSysteme()) {
                roleEnum(role.getCode()).ifPresent(rolesSystemeAssignes::add);
            }
        }

        // Garde la colonne legacy `role` cohérente avec les affectations (elle pilote encore la
        // nav/guards/garde-fous). On conserve le rôle actuel s'il est toujours attribué, sinon on
        // bascule sur un des rôles système attribués.
        if (!rolesSystemeAssignes.isEmpty() && !rolesSystemeAssignes.contains(membre.getRole())) {
            membre.setRole(rolesSystemeAssignes.get(0));
            utilisateurRepo.save(membre);
        }

        return affectationRepo.findByUserIdAndClubId(membreId, clubId).stream()
                .map(this::toAffectationDto).toList();
    }

    /** Mappe le code d'un rôle système vers l'enum Role (ENTRAINEUR, PREPARATEUR…). */
    private java.util.Optional<Role> roleEnum(String code) {
        try {
            return java.util.Optional.of(Role.valueOf(code));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }

    // ─────────────────────────── Helpers ───────────────────────────

    private RoleDto toRoleDto(RoleApplicatif r) {
        return new RoleDto(r.getId(), r.getCode(), r.getLibelle(), r.isSysteme(),
                r.getClubId() == null,   // global = sans club (système prédéfini ou global custom)
                permissionsDuRole(r.getId()), affectationRepo.countByRoleId(r.getId()));
    }

    private AffectationDto toAffectationDto(AffectationRole a) {
        RoleApplicatif r = roleRepo.findById(a.getRoleId()).orElse(null);
        String equipeNom = a.getEquipeId() == null ? null
                : equipeRepo.findById(a.getEquipeId()).map(Equipe::getNom).orElse(null);
        return new AffectationDto(a.getId(), a.getRoleId(),
                r != null ? r.getLibelle() : "?", r != null && r.isSysteme(),
                a.getEquipeId(), equipeNom);
    }

    private List<String> permissionsDuRole(UUID roleId) {
        return rolePermRepo.findByRoleId(roleId).stream().map(RolePermission::getPermission).toList();
    }

    /** Ne garde que des codes de permission connus (catalogue figé), dédoublonnés. */
    private List<String> valideEtFiltre(List<String> codes) {
        return codes.stream().distinct()
                .filter(c -> Permission.parCode(c) != null)
                .toList();
    }

    /** Anti-escalade : on n'accorde que des permissions que l'acteur détient lui-même. */
    private void verifieNonEscalade(Utilisateur acteur, List<String> perms) {
        // Droits RBAC BRUTS (avant filtre modules) : l'abonnement du club ne doit pas restreindre
        // la délégation — un module off rend la permission dormante, pas interdite à attribuer.
        Set<String> miennes = permissionResolver.permissionsPour(acteur, false);
        for (String p : perms) {
            if (!miennes.contains(p)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Vous ne pouvez pas accorder une permission que vous ne détenez pas : " + p);
            }
        }
    }

    /** Remplace les permissions du rôle (exécuté dans la transaction de l'appelant). */
    private void remplacePermissions(UUID roleId, List<String> perms) {
        rolePermRepo.deleteByRoleId(roleId);
        for (String code : perms) {
            RolePermission rp = new RolePermission();
            rp.setRoleId(roleId);
            rp.setPermission(code);
            rolePermRepo.save(rp);
        }
    }

    private RoleApplicatif chargeRoleCustomDuClub(Utilisateur acteur, UUID roleId) {
        UUID clubId = exigeClub(acteur);
        RoleApplicatif r = roleRepo.findById(roleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rôle introuvable"));
        if (r.isSysteme() || !clubId.equals(r.getClubId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Rôle non modifiable");
        }
        return r;
    }

    /** Rôle utilisable dans le club : global (système ou global custom), ou custom appartenant à ce club. */
    private RoleApplicatif chargeRoleDisponible(UUID roleId, UUID clubId) {
        RoleApplicatif r = roleRepo.findById(roleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rôle introuvable"));
        if (r.getClubId() != null && !clubId.equals(r.getClubId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Rôle hors de votre club");
        }
        return r;
    }

    private Utilisateur chargeStaffDuClub(UUID membreId, UUID clubId) {
        Utilisateur m = utilisateurRepo.findById(membreId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membre introuvable"));
        if (!clubId.equals(m.getClubId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Membre hors de votre club");
        }
        if (!STAFF_AFFECTABLE.contains(m.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Seuls les membres du staff reçoivent des rôles (le joueur garde son espace perso)");
        }
        return m;
    }

    private void verifieEquipeDuClub(UUID equipeId, UUID clubId) {
        Equipe e = equipeRepo.findById(equipeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Équipe introuvable"));
        if (!clubId.equals(e.getClubId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Équipe hors de votre club");
        }
    }

    private String genererCode(UUID clubId, String libelle) {
        String base = libelle.toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
        base = "C_" + (base.isBlank() ? "ROLE" : base);
        if (base.length() > 46) base = base.substring(0, 46);
        Set<String> existants = roleRepo.findByClubId(clubId).stream()
                .map(RoleApplicatif::getCode).collect(Collectors.toSet());
        String code = base;
        int i = 1;
        while (existants.contains(code)) {
            code = base + "_" + (++i);
        }
        return code;
    }

    /**
     * Club sur lequel agit l'acteur : son club, ou pour le super-admin le club « entré »
     * via le contexte de navigation actif.
     */
    private UUID exigeClub(Utilisateur acteur) {
        if (acteur.getClubId() != null) {
            return acteur.getClubId();
        }
        if (acteur.getRole() == Role.SUPER_ADMIN) {
            ContexteActif ctx = ContexteActifHolder.get();
            if (ctx != null && ctx.clubId() != null) {
                return ctx.clubId();
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Entrez d'abord dans un club");
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Aucun club associé");
    }
}
