package com.remipreparateur.club.service;

import com.remipreparateur.club.dto.GestionDtos.*;
import com.remipreparateur.club.entity.Club;
import com.remipreparateur.club.entity.Equipe;
import com.remipreparateur.auth.entity.Role;
import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.club.repository.ClubRepository;
import com.remipreparateur.club.repository.EquipeRepository;
import com.remipreparateur.auth.repository.UtilisateurRepository;
import com.remipreparateur.auth.rbac.AffectationRole;
import com.remipreparateur.auth.rbac.AffectationRoleRepository;
import com.remipreparateur.auth.rbac.Permission;
import com.remipreparateur.auth.rbac.RoleApplicatifRepository;
import com.remipreparateur.auth.rbac.RolePermissionRepository;
import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.saison.repository.EffectifSaisonRepository;
import com.remipreparateur.saison.service.AppartenanceService;
import com.remipreparateur.shared.security.ContexteActif;
import com.remipreparateur.shared.security.ContexteActifHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Gestion des equipes et membres d'un club, du point de vue de son president. */
@Service
public class GestionClubService {

    private static final int MAX_EQUIPES = 3;
    private static final Set<Role> ROLES_MEMBRES = EnumSet.of(
            Role.ENTRAINEUR, Role.PREPARATEUR, Role.MEDICAL, Role.ADMINISTRATIF, Role.JOUEUR);
    /** Rôles staff dotés d'une affectation RBAC « principale » (le joueur reste en self-scope). */
    private static final Set<Role> ROLES_STAFF = EnumSet.of(
            Role.ENTRAINEUR, Role.PREPARATEUR, Role.MEDICAL, Role.ADMINISTRATIF);

    private final EquipeRepository equipeRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ClubRepository clubRepository;
    private final JoueurRepository joueurRepository;
    private final EffectifSaisonRepository effectifRepository;
    private final AppartenanceService appartenance;
    private final PasswordEncoder passwordEncoder;
    private final RoleApplicatifRepository roleRepository;
    private final AffectationRoleRepository affectationRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public GestionClubService(EquipeRepository equipeRepository,
                              UtilisateurRepository utilisateurRepository,
                              ClubRepository clubRepository,
                              JoueurRepository joueurRepository,
                              EffectifSaisonRepository effectifRepository,
                              AppartenanceService appartenance,
                              PasswordEncoder passwordEncoder,
                              RoleApplicatifRepository roleRepository,
                              AffectationRoleRepository affectationRepository,
                              RolePermissionRepository rolePermissionRepository) {
        this.equipeRepository = equipeRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.clubRepository = clubRepository;
        this.joueurRepository = joueurRepository;
        this.effectifRepository = effectifRepository;
        this.appartenance = appartenance;
        this.passwordEncoder = passwordEncoder;
        this.roleRepository = roleRepository;
        this.affectationRepository = affectationRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    // ── Vue agregee ──
    public MonClubResponse monClub(Utilisateur acteur) {
        UUID clubId = exigeClub(acteur);
        Club club = clubRepository.findById(clubId).orElse(null);
        Autorite a = autoriteDe(acteur, clubId);
        List<EquipeResponse> equipes = listerEquipes(clubId);
        List<MembreResponse> membres = listerMembres(clubId);
        // Acteur scopé à une/des équipe(s) (ex. entraîneur) : ne voit que SON périmètre.
        if (!a.clubWide()) {
            equipes = equipes.stream().filter(e -> a.equipes().contains(e.id())).toList();
            membres = membres.stream()
                    .filter(m -> m.equipeId() != null && a.equipes().contains(m.equipeId()))
                    .toList();
        }
        return new MonClubResponse(
                clubId,
                club != null ? club.getNom() : null,
                club != null ? club.getLogo() : null,
                equipes,
                membres);
    }

    // ── Equipes ──
    @Transactional
    public EquipeResponse creerEquipe(Utilisateur president, EquipeRequest req) {
        UUID clubId = exigeClub(president);
        if (equipeRepository.countByClubId(clubId) >= MAX_EQUIPES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Maximum " + MAX_EQUIPES + " equipes par club");
        }
        Equipe e = new Equipe();
        e.setNom(req.nom());
        e.setCategorie(req.categorie());
        e.setClubId(clubId);
        e = equipeRepository.save(e);
        return toEquipeResponse(e);
    }

    public List<EquipeResponse> listerEquipes(UUID clubId) {
        return equipeRepository.findByClubId(clubId).stream().map(this::toEquipeResponse).toList();
    }

    @Transactional
    public EquipeResponse modifierEquipe(Utilisateur president, UUID equipeId, EquipeRequest req) {
        Equipe e = equipeRepository.findById(equipeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipe introuvable"));
        verifieMemeClub(president, e.getClubId());
        e.setNom(req.nom());
        e.setCategorie(req.categorie());
        return toEquipeResponse(equipeRepository.save(e));
    }

    @Transactional
    public void supprimerEquipe(Utilisateur president, UUID equipeId) {
        Equipe e = equipeRepository.findById(equipeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipe introuvable"));
        verifieMemeClub(president, e.getClubId());
        equipeRepository.deleteById(equipeId);
    }

    // ── Membres ──
    @Transactional
    public MembreResponse creerMembre(Utilisateur president, MembreCreateRequest req) {
        UUID clubId = exigeClub(president);
        Role role = parseRoleMembre(req.role());

        if (utilisateurRepository.existsByEmailIgnoreCase(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email deja utilise");
        }
        if (req.equipeId() != null) {
            verifieEquipeDuClub(req.equipeId(), clubId);
        }

        Utilisateur m = new Utilisateur();
        m.setEmail(req.email());
        m.setMotDePasse(passwordEncoder.encode(req.motDePasse()));
        m.setNom(req.nom());
        m.setPrenom(req.prenom());
        m.setRole(role);
        m.setSpecialite(req.specialite());
        m.setClubId(clubId);
        m.setEquipeId(req.equipeId());

        // Lien optionnel à une fiche dès la création (compte JOUEUR uniquement).
        if (req.joueurId() != null) {
            if (role != Role.JOUEUR) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seul un compte joueur peut etre lie a une fiche");
            }
            Joueur fiche = joueurRepository.findById(req.joueurId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fiche joueur introuvable"));
            UUID ficheEquipe = appartenance.equipePrincipale(fiche.getId());   // équipe dérivée (Phase 4)
            if (ficheEquipe != null) verifieEquipeDuClub(ficheEquipe, clubId);
            if (utilisateurRepository.existsByJoueurId(req.joueurId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Fiche deja reliee a un autre compte");
            }
            m.setJoueurId(req.joueurId());
            if (ficheEquipe != null) m.setEquipeId(ficheEquipe);
        }

        // Hiérarchie + périmètre : on ne crée qu'un membre de rang inférieur, dans son équipe.
        verifiePeutGerer(president, niveauRole(role), m.getEquipeId(), clubId);

        m = utilisateurRepository.save(m);
        synchroniserAffectationPrincipale(m, clubId);

        // Conformité documentaire du staff (Phase 3) : un membre encadrant obtient une fiche
        // `personne` au niveau club (support des documents : licence dirigeant, diplôme,
        // honorabilité...). La fiche reste hors des listes joueurs (Phase 2). Les comptes JOUEUR
        // sont exclus : leur fiche est la fiche d'effectif, gérée séparément (lien explicite).
        if (ROLES_STAFF.contains(role) && m.getJoueurId() == null) {
            Joueur fiche = creerFicheStaff(m, clubId);
            m.setJoueurId(fiche.getId());
            m = utilisateurRepository.save(m);
        }
        return toMembreResponse(m);
    }

    public List<MembreResponse> listerMembres(UUID clubId) {
        return utilisateurRepository.findByClubId(clubId).stream()
                .filter(u -> ROLES_MEMBRES.contains(u.getRole()))
                .map(this::toMembreResponse)
                .toList();
    }

    @Transactional
    public MembreResponse modifierMembre(Utilisateur president, UUID membreId, MembreUpdateRequest req) {
        Utilisateur m = chargeMembreDuClub(president, membreId);
        UUID clubId = m.getClubId();
        // Droit de gérer la cible TELLE QU'ELLE EST aujourd'hui.
        verifiePeutGerer(president, niveauDe(m, clubId), m.getEquipeId(), clubId);

        Role ancienRole = m.getRole();
        UUID ancienneEquipe = m.getEquipeId();

        if (req.role() != null) m.setRole(parseRoleMembre(req.role()));
        if (req.specialite() != null) m.setSpecialite(req.specialite());
        if (req.equipeId() != null) {
            verifieEquipeDuClub(req.equipeId(), m.getClubId());
            m.setEquipeId(req.equipeId());
        }
        if (req.actif() != null) m.setActif(req.actif());

        // Droit sur la cible APRÈS modif (interdit toute élévation de rang / sortie de périmètre).
        verifiePeutGerer(president, niveauRole(m.getRole()), m.getEquipeId(), clubId);
        m = utilisateurRepository.save(m);

        if (m.getRole() != ancienRole) {
            // Changement de rôle « principal » → on réinitialise les affectations à ce seul rôle
            // (le cumul de rôles se gère ensuite dans l'onglet « Rôles & accès »).
            synchroniserAffectationPrincipale(m, clubId);
        } else if (!java.util.Objects.equals(m.getEquipeId(), ancienneEquipe)) {
            // Le membre change d'équipe : on repointe ses affectations d'équipe sur la nouvelle.
            repointerAffectationsSurEquipe(m, clubId);
        }
        return toMembreResponse(m);
    }

    // ── Theme visuel du club ──

    /** Theme du club de l'utilisateur (tous roles, joueur inclus). Sans club (super-admin hors contexte) : defaut. */
    public ThemeResponse theme(Utilisateur acteur) {
        UUID clubId = acteur.getClubId();
        if (clubId == null && acteur.getRole() == Role.SUPER_ADMIN) {
            ContexteActif ctx = ContexteActifHolder.get();
            clubId = ctx != null ? ctx.clubId() : null;
        }
        if (clubId == null) return new ThemeResponse(null, false);
        Club club = clubRepository.findById(clubId).orElse(null);
        if (club == null) return new ThemeResponse(null, false);
        return new ThemeResponse(club.getCouleurAccent(), club.isNavTeintee());
    }

    /** Definit le theme du club (president via club:manage, super-admin via contexte). */
    @Transactional
    public ThemeResponse definirTheme(Utilisateur acteur, ThemeRequest req) {
        UUID clubId = exigeClub(acteur);
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Club introuvable"));
        if (req.couleurAccent() != null && !req.couleurAccent().isBlank()) {
            if (!req.couleurAccent().matches("#[0-9a-fA-F]{6}")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Couleur invalide (attendu : #RRGGBB)");
            }
            club.setCouleurAccent(req.couleurAccent().toUpperCase());
        } else {
            club.setCouleurAccent(null);
        }
        if (req.navTeintee() != null) club.setNavTeintee(req.navTeintee());
        club = clubRepository.save(club);
        return new ThemeResponse(club.getCouleurAccent(), club.isNavTeintee());
    }

    /** Change l'email et/ou le mot de passe d'un membre (président sur son club, super-admin via contexte). */
    @Transactional
    public MembreResponse modifierIdentifiants(Utilisateur acteur, UUID membreId, IdentifiantsUpdateRequest req) {
        Utilisateur m = chargeMembreDuClub(acteur, membreId);
        verifiePeutGerer(acteur, niveauDe(m, m.getClubId()), m.getEquipeId(), m.getClubId());

        if (req.email() != null && !req.email().isBlank()) {
            String email = req.email().trim();
            if (!email.equalsIgnoreCase(m.getEmail())
                    && utilisateurRepository.existsByEmailIgnoreCase(email)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email deja utilise");
            }
            m.setEmail(email);
        }
        if (req.nouveauMotDePasse() != null && !req.nouveauMotDePasse().isBlank()) {
            if (req.nouveauMotDePasse().length() < 8) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mot de passe : 8 caracteres minimum");
            }
            m.setMotDePasse(passwordEncoder.encode(req.nouveauMotDePasse()));
        }
        return toMembreResponse(utilisateurRepository.save(m));
    }

    @Transactional
    public void supprimerMembre(Utilisateur president, UUID membreId) {
        Utilisateur m = chargeMembreDuClub(president, membreId);
        verifiePeutGerer(president, niveauDe(m, m.getClubId()), m.getEquipeId(), m.getClubId());
        UUID ficheStaff = (ROLES_STAFF.contains(m.getRole()) && m.getJoueurId() != null)
                ? m.getJoueurId() : null;
        utilisateurRepository.deleteById(m.getId());
        // Nettoyage de la fiche staff auto-créée (Phase 3) pour éviter une fiche fantôme qui
        // redeviendrait un « joueur » au sens de la Phase 2 (plus de compte, aucun effectif).
        // Garde : on ne supprime jamais une fiche présente dans un effectif (vrai joueur / cumul).
        if (ficheStaff != null && !effectifRepository.existsByJoueurId(ficheStaff)) {
            joueurRepository.deleteById(ficheStaff); // cascade → document_joueur (V47)
        }
    }

    /**
     * Crée une fiche `personne` au niveau club pour un membre STAFF (support de la conformité
     * documentaire — Phase 3). Champs sportifs à null, aucune équipe (fiche non assignée) :
     * elle reste hors des listes joueurs (Phase 2, dérivation par appartenance).
     */
    private Joueur creerFicheStaff(Utilisateur membre, UUID clubId) {
        Joueur f = new Joueur();
        f.setNom(membre.getNom() != null && !membre.getNom().isBlank() ? membre.getNom() : "—");
        f.setPrenom(membre.getPrenom() != null && !membre.getPrenom().isBlank() ? membre.getPrenom() : "—");
        f.setClubId(clubId);
        f.setStatut("actif");
        return joueurRepository.save(f);
    }

    // ── Liaison compte JOUEUR ↔ fiche (posée a posteriori) ──
    @Transactional
    public MembreResponse lierFiche(Utilisateur acteur, UUID membreId, UUID joueurId) {
        Utilisateur m = chargeMembreDuClub(acteur, membreId);
        if (m.getRole() != Role.JOUEUR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seul un compte joueur peut etre lie a une fiche");
        }
        UUID clubId = exigeClub(acteur);
        Joueur fiche = joueurRepository.findById(joueurId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fiche joueur introuvable"));
        // La fiche appartient au CLUB (niveau club) ; son équipe est OPTIONNELLE (fiche non
        // assignée). On vérifie l'appartenance au club, pas la présence d'une équipe.
        UUID ficheClub = clubDeFiche(fiche);
        if (ficheClub == null || !ficheClub.equals(clubId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fiche hors de votre club");
        }
        UUID ficheEquipe = appartenance.equipePrincipale(fiche.getId());   // équipe dérivée (Phase 4)
        if (ficheEquipe != null) {
            verifieEquipeDuClub(ficheEquipe, clubId);
        }
        // Contrôle hiérarchie/périmètre : sur l'équipe de la fiche si elle en a une, sinon au
        // niveau club (les rôles équipe-scopés ne peuvent lier qu'une fiche de leur équipe).
        verifiePeutGerer(acteur, niveauRole(Role.JOUEUR), ficheEquipe, clubId);
        if (utilisateurRepository.existsByJoueurIdAndIdNot(joueurId, membreId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Fiche deja reliee a un autre compte");
        }
        m.setJoueurId(joueurId);
        // Aligne l'équipe du compte sur celle de la fiche UNIQUEMENT si la fiche en a une.
        if (ficheEquipe != null) {
            m.setEquipeId(ficheEquipe);
        }
        return toMembreResponse(utilisateurRepository.save(m));
    }

    @Transactional
    public MembreResponse delierFiche(Utilisateur acteur, UUID membreId) {
        Utilisateur m = chargeMembreDuClub(acteur, membreId);
        verifiePeutGerer(acteur, niveauDe(m, m.getClubId()), m.getEquipeId(), m.getClubId());
        m.setJoueurId(null);
        return toMembreResponse(utilisateurRepository.save(m));
    }

    // ── Helpers ──

    /**
     * Club sur lequel agit l'utilisateur : son club de rattachement (président,
     * entraîneur, préparateur…) ou, pour le SUPER_ADMIN sans club, le club « entré »
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
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Aucun club associe");
    }

    private void verifieMemeClub(Utilisateur acteur, UUID clubId) {
        if (!exigeClub(acteur).equals(clubId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ressource hors de votre club");
        }
    }

    private void verifieEquipeDuClub(UUID equipeId, UUID clubId) {
        Equipe e = equipeRepository.findById(equipeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipe introuvable"));
        if (!e.getClubId().equals(clubId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Equipe hors de votre club");
        }
    }

    /** Club d'une fiche : son {@code club_id} direct, sinon dérivé de son équipe (fiches legacy). */
    private UUID clubDeFiche(Joueur fiche) {
        return fiche.getClubId();   // Phase 4 : le club est porté par la fiche (plus de dérivation via l'équipe)
    }

    // ── Hiérarchie & périmètre de gestion des membres ──

    /**
     * Autorité de gestion d'un utilisateur sur les MEMBRES, déduite de ses affectations RBAC
     * (permission {@code membres:manage}) dans le club actif :
     *   niveau 4 = président / super-admin · 3 = chef (membres:manage club-wide) ·
     *   2 = staff scopé équipe (entraîneur) · 1 = aucun pouvoir de gestion (joueur, prépa, médical…).
     */
    private record Autorite(int niveau, boolean clubWide, Set<UUID> equipes) {}

    private Autorite autoriteDe(Utilisateur u, UUID clubId) {
        if (u.getRole() == Role.SUPER_ADMIN || u.getRole() == Role.PRESIDENT) {
            return new Autorite(4, true, Set.of());
        }
        boolean clubWide = false;
        Set<UUID> equipes = new HashSet<>();
        for (AffectationRole a : affectationRepository.findByUserIdAndClubId(u.getId(), clubId)) {
            if (rolePermissionRepository.existsByRoleIdAndPermission(
                    a.getRoleId(), Permission.MEMBRES_MANAGE.getCode())) {
                if (a.getEquipeId() == null) clubWide = true;
                else equipes.add(a.getEquipeId());
            }
        }
        if (clubWide) return new Autorite(3, true, Set.of());
        if (!equipes.isEmpty()) return new Autorite(2, false, equipes);
        return new Autorite(1, false, Set.of());
    }

    /** Niveau hiérarchique d'un membre EXISTANT (selon ses affectations effectives). */
    private int niveauDe(Utilisateur u, UUID clubId) {
        return autoriteDe(u, clubId).niveau();
    }

    /** Niveau d'un membre selon le rôle « principal » visé (création / changement de rôle). */
    private int niveauRole(Role r) {
        return switch (r) {
            case SUPER_ADMIN, PRESIDENT -> 4;
            case ENTRAINEUR -> 2;                 // l'entraîneur gère les membres de son équipe
            default -> 1;                         // PREPARATEUR, MEDICAL, ADMINISTRATIF, JOUEUR
        };
    }

    /**
     * Autorise une action de gestion sur un membre de niveau {@code cibleNiveau} appartenant à
     * {@code cibleEquipeId} : l'acteur doit avoir la gestion des membres, un rang STRICTEMENT
     * supérieur à la cible, et le couvrir dans son périmètre (club entier ou équipe précise).
     */
    private void verifiePeutGerer(Utilisateur acteur, int cibleNiveau, UUID cibleEquipeId, UUID clubId) {
        Autorite a = autoriteDe(acteur, clubId);
        if (a.niveau() < 2) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vous n'avez pas la gestion des membres");
        }
        if (a.niveau() <= cibleNiveau) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Action non autorisée sur un membre de rang égal ou supérieur");
        }
        if (!a.clubWide() && (cibleEquipeId == null || !a.equipes().contains(cibleEquipeId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Membre hors de votre équipe");
        }
    }

    private Utilisateur chargeMembreDuClub(Utilisateur president, UUID membreId) {
        Utilisateur m = utilisateurRepository.findById(membreId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membre introuvable"));
        verifieMemeClub(president, m.getClubId());
        if (!ROLES_MEMBRES.contains(m.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Membre non gerable");
        }
        return m;
    }

    private Role parseRoleMembre(String role) {
        Role r;
        try {
            r = Role.valueOf(role);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role invalide : " + role);
        }
        if (!ROLES_MEMBRES.contains(r)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role non autorise pour un membre : " + role);
        }
        return r;
    }

    private EquipeResponse toEquipeResponse(Equipe e) {
        long nbMembres = utilisateurRepository.findByEquipeId(e.getId()).size();
        return new EquipeResponse(e.getId(), e.getNom(), e.getCategorie(), e.getClubId(), nbMembres);
    }

    private MembreResponse toMembreResponse(Utilisateur u) {
        return new MembreResponse(
                u.getId(), u.getEmail(), u.getNom(), u.getPrenom(),
                u.getRole().name(), u.getSpecialite(), u.getEquipeId(), u.getJoueurId(), u.isActif());
    }

    // ── Synchronisation du rôle « principal » (legacy) avec une affectation RBAC ──

    /**
     * Réinitialise les affectations du membre dans le club à son SEUL rôle principal (système).
     * Garantit qu'un membre fraîchement créé (ou dont on change le rôle) possède bien des
     * permissions. Le cumul de rôles supplémentaires se gère ensuite via « Rôles & accès ».
     */
    private void synchroniserAffectationPrincipale(Utilisateur membre, UUID clubId) {
        affectationRepository.deleteByUserIdAndClubId(membre.getId(), clubId);
        if (!ROLES_STAFF.contains(membre.getRole())) {
            return; // joueur (self-scope) → pas d'affectation
        }
        // L'ADMINISTRATIF pilote le club ENTIER → affectation club-wide (equipe_id NULL), même
        // sans équipe (c'est son cas normal). Les autres rôles staff restent scopés à leur équipe
        // (et sans équipe → pas d'affectation, comportement historique).
        boolean clubWide = membre.getRole() == Role.ADMINISTRATIF;
        if (!clubWide && membre.getEquipeId() == null) {
            return;
        }
        roleRepository.findBySystemeTrueAndCode(membre.getRole().name()).ifPresent(role -> {
            AffectationRole a = new AffectationRole();
            a.setUserId(membre.getId());
            a.setClubId(clubId);
            a.setEquipeId(clubWide ? null : membre.getEquipeId());
            a.setRoleId(role.getId());
            affectationRepository.save(a);
        });
    }

    /** Repointe les affectations scopées équipe du membre sur sa nouvelle équipe (cumul préservé). */
    private void repointerAffectationsSurEquipe(Utilisateur membre, UUID clubId) {
        for (AffectationRole a : affectationRepository.findByUserIdAndClubId(membre.getId(), clubId)) {
            if (a.getEquipeId() != null) {
                a.setEquipeId(membre.getEquipeId());
                affectationRepository.save(a);
            }
        }
    }
}
