package com.remipreparateur.performance.referentiel.service;

import com.remipreparateur.auth.entity.Role;
import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.auth.rbac.FeatureModule;
import com.remipreparateur.auth.rbac.PermissionResolver;
import com.remipreparateur.club.pack.ClubModulesService;
import com.remipreparateur.club.repository.ClubRepository;
import com.remipreparateur.club.repository.EquipeRepository;
import com.remipreparateur.performance.referentiel.MetriqueCharge;
import com.remipreparateur.performance.referentiel.PosteReference;
import com.remipreparateur.performance.referentiel.dto.ReferentielDtos.*;
import com.remipreparateur.performance.referentiel.entity.ClubReferentiel;
import com.remipreparateur.performance.referentiel.entity.ReferentielObjectif;
import com.remipreparateur.performance.referentiel.entity.ReferentielObjectifValeur;
import com.remipreparateur.performance.referentiel.repository.ClubReferentielRepository;
import com.remipreparateur.performance.referentiel.repository.ReferentielObjectifRepository;
import com.remipreparateur.performance.referentiel.repository.ReferentielObjectifValeurRepository;
import com.remipreparateur.shared.security.CurrentUserProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Catalogue des référentiels de charge et adoption par les clubs (lot 1).
 *
 * <p>Le référentiel répond à UNE question : « pour un joueur de ce poste, à ce niveau, qu'est-ce
 * qui est normal ? ». C'est une norme fournie, pas un objectif décidé — et elle a de la valeur
 * toute seule, avant tout objectif de période : afficher « Habituel 24 km / Attendu N1 31–36 km »
 * suffit déjà à dire ce que l'application ne savait pas dire.
 *
 * <p>Deux invariants tiennent tout le reste :
 * <ol>
 *   <li><b>Un référentiel PUBLIÉ est immuable.</b> Toute correction passe par une nouvelle version.
 *       Sans ça, une retouche du super-admin déplacerait l'« Attendu » de tous les clubs sans
 *       prévenir, et un joueur passerait de vert à rouge sans qu'aucun geste n'ait été fait.</li>
 *   <li><b>Le club est épinglé sur SA version.</b> Publier une v2 ne bouge rien chez lui : on lui
 *       signale, il migre quand il veut.</li>
 * </ol>
 */
@Service
public class ReferentielObjectifService {

    private final ReferentielObjectifRepository referentielRepository;
    private final ReferentielObjectifValeurRepository valeurRepository;
    private final ClubReferentielRepository adoptionRepository;
    private final EquipeRepository equipeRepository;
    private final ClubRepository clubRepository;
    private final PermissionResolver permissionResolver;
    private final ClubModulesService clubModulesService;
    private final CurrentUserProvider currentUser;

    public ReferentielObjectifService(ReferentielObjectifRepository referentielRepository,
                                      ReferentielObjectifValeurRepository valeurRepository,
                                      ClubReferentielRepository adoptionRepository,
                                      EquipeRepository equipeRepository,
                                      ClubRepository clubRepository,
                                      PermissionResolver permissionResolver,
                                      ClubModulesService clubModulesService,
                                      CurrentUserProvider currentUser) {
        this.referentielRepository = referentielRepository;
        this.valeurRepository = valeurRepository;
        this.adoptionRepository = adoptionRepository;
        this.equipeRepository = equipeRepository;
        this.clubRepository = clubRepository;
        this.permissionResolver = permissionResolver;
        this.clubModulesService = clubModulesService;
        this.currentUser = currentUser;
    }

    // ──────────────────────── Vocabulaire ────────────────────────

    /** Métriques, postes, contextes et catalogue visible : de quoi peindre l'écran en un appel. */
    @Transactional(readOnly = true)
    public CatalogueResponse catalogue() {
        exigeModule();
        UUID clubId = permissionResolver.clubActif(currentUser.current());
        List<ReferentielObjectif> visibles = clubId != null
                ? referentielRepository.cataloguePourClub(clubId)
                : referentielRepository.findByClubIdIsNullOrderByNiveauAscVersionDesc();
        return new CatalogueResponse(
                MetriqueCharge.toutes().stream()
                        .map(m -> new MetriqueDto(m.getCode(), m.getLibelle(), m.getUnite(),
                                m.getNature().name(), m.isPrincipale(), m.getOrdre())).toList(),
                PosteReference.toutes().stream()
                        .map(p -> new PosteDto(p.getCode(), p.getLibelle(), p.getOrdre())).toList(),
                List.of(ReferentielObjectifValeur.CONTEXTE_MATCH,
                        ReferentielObjectifValeur.CONTEXTE_SEMAINE),
                visibles.stream().map(this::toResume).toList());
    }

    // ──────────────────────── Côté super-admin ────────────────────────

    /** Tout le catalogue plateforme, brouillons et archives compris. */
    @Transactional(readOnly = true)
    public List<ReferentielResume> listerPlateforme() {
        return referentielRepository.findByClubIdIsNullOrderByNiveauAscVersionDesc()
                .stream().map(this::toResume).toList();
    }

    /** Crée un référentiel plateforme VIDE, en brouillon. */
    @Transactional
    public ReferentielDetail creerPlateforme(ReferentielRequest req) {
        ReferentielObjectif r = new ReferentielObjectif();
        r.setClubId(null);
        r.setNom(exigeNom(req.nom()));
        r.setNiveau(req.niveau());
        r.setCreePar(currentUser.current().getId());
        r = referentielRepository.save(r);
        if (req.valeurs() != null && !req.valeurs().isEmpty()) {
            remplacerValeurs(r, req.valeurs());
        }
        return detail(r.getId());
    }

    /**
     * Ouvre une NOUVELLE VERSION d'un référentiel publié : copie intégrale en brouillon, avec
     * {@code parentId} sur l'original. L'original reste en ligne et continue de servir les clubs
     * épinglés dessus tant que la nouvelle version n'est pas publiée.
     */
    @Transactional
    public ReferentielDetail nouvelleVersion(UUID id) {
        ReferentielObjectif source = exigeReferentiel(id);
        if (!ReferentielObjectif.STATUT_PUBLIE.equals(source.getStatut())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Seul un référentiel publié peut donner lieu à une nouvelle version.");
        }
        ReferentielObjectif copie = copier(source, source.getClubId(),
                source.getNom(), source.getNiveau());
        copie.setParentId(source.getId());
        copie.setVersion(source.getVersion() + 1);
        copie.setSourceId(source.getSourceId());
        referentielRepository.save(copie);
        return detail(copie.getId());
    }

    /**
     * Publie un brouillon. S'il s'agit d'une nouvelle version, le parent passe en ARCHIVE : il
     * reste lisible (les clubs épinglés dessus continuent de fonctionner) mais disparaît des
     * choix d'adoption.
     */
    @Transactional
    public ReferentielResume publier(UUID id) {
        ReferentielObjectif r = exigeReferentiel(id);
        if (!r.estModifiable()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce référentiel est déjà publié.");
        }
        if (valeurRepository.findByReferentielId(id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Impossible de publier un référentiel sans aucune valeur.");
        }
        r.setStatut(ReferentielObjectif.STATUT_PUBLIE);
        r.setUpdatedAt(LocalDateTime.now());
        referentielRepository.save(r);
        if (r.getParentId() != null) {
            referentielRepository.findById(r.getParentId()).ifPresent(parent -> {
                parent.setStatut(ReferentielObjectif.STATUT_ARCHIVE);
                parent.setUpdatedAt(LocalDateTime.now());
                referentielRepository.save(parent);
            });
        }
        return toResume(r);
    }

    // ──────────────────────── Lecture & écriture des valeurs ────────────────────────

    @Transactional(readOnly = true)
    public ReferentielDetail detail(UUID id) {
        ReferentielObjectif r = exigeReferentiel(id);
        exigeLisible(r);
        List<ValeurDto> valeurs = valeurRepository.findByReferentielId(id).stream()
                .map(v -> new ValeurDto(v.getPoste(), v.getContexte(), v.getMetrique(),
                        v.getValeurMin(), v.getValeurMax()))
                .sorted(ORDRE_AFFICHAGE)
                .toList();
        return new ReferentielDetail(toResume(r), valeurs);
    }

    /**
     * Remplace les valeurs d'un BROUILLON. Refuse sur un référentiel publié — c'est l'invariant
     * qui protège les clubs d'un déplacement silencieux de leur « Attendu ».
     */
    @Transactional
    public ReferentielDetail enregistrerValeurs(UUID id, List<ValeurDto> valeurs) {
        ReferentielObjectif r = exigeReferentiel(id);
        exigeEditable(r);
        if (!r.estModifiable()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un référentiel publié est immuable : ouvrez une nouvelle version pour le corriger.");
        }
        remplacerValeurs(r, valeurs == null ? List.of() : valeurs);
        return detail(id);
    }

    // ──────────────────────── Côté club ────────────────────────

    /**
     * Duplique un référentiel du catalogue chez le club, pour personnalisation.
     *
     * <p>Le club ne part JAMAIS d'une grille vide : 6 postes × 7 métriques × 2 contextes, personne
     * ne remplit ça, et les tentatives produisent des données incohérentes. On duplique, on ajuste.
     */
    @Transactional
    public ReferentielDetail dupliquerPourClub(DuplicationRequest req) {
        exigeModule();
        UUID clubId = clubActif();
        ReferentielObjectif source = exigeReferentiel(req.sourceId());
        exigeLisible(source);
        ReferentielObjectif copie = copier(source, clubId,
                req.nom() != null && !req.nom().isBlank() ? req.nom().trim()
                        : source.getNom() + " (adapté)",
                req.niveau() != null ? req.niveau() : source.getNiveau());
        // La copie de club naît en BROUILLON pour être modifiable, et garde le lien vers la source
        // plateforme : c'est ce qui permet d'afficher l'écart au standard, case par case.
        copie.setSourceId(source.estPlateforme() ? source.getId() : source.getSourceId());
        copie.setVersion(1);
        referentielRepository.save(copie);
        return detail(copie.getId());
    }

    /** Les adoptions du club (défaut + surcharges par équipe), avec le signal de nouvelle version. */
    @Transactional(readOnly = true)
    public List<AdoptionDto> adoptions() {
        exigeModule();
        UUID clubId = clubActif();
        return adoptionRepository.findByClubId(clubId).stream().map(a -> {
            ReferentielObjectif r = referentielRepository.findById(a.getReferentielId()).orElse(null);
            ReferentielObjectif plusRecent = r == null ? null : versionPlusRecente(r);
            return new AdoptionDto(
                    a.getId(), a.getEquipeId(),
                    a.getEquipeId() == null ? "Tout le club"
                            : equipeRepository.findById(a.getEquipeId())
                                .map(e -> e.getNom()).orElse("Équipe"),
                    a.getReferentielId(), r == null ? null : r.getNom(),
                    r == null ? 0 : r.getVersion(),
                    plusRecent == null ? null : plusRecent.getId(),
                    plusRecent == null ? null : plusRecent.getNom());
        }).toList();
    }

    /**
     * Adopte un référentiel, pour tout le club ({@code equipeId} nul) ou pour UNE équipe.
     *
     * <p>La surcharge par équipe n'est pas un raffinement : un club a des séniors en N1, une
     * réserve en régional et des U19. Une adoption unique de club serait fausse pour deux d'entre
     * elles.
     */
    @Transactional
    public AdoptionDto adopter(AdoptionRequest req) {
        exigeModule();
        UUID clubId = clubActif();
        ReferentielObjectif r = exigeReferentiel(req.referentielId());
        exigeLisible(r);
        if (r.estModifiable() && r.estPlateforme()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ce référentiel est encore en brouillon : il n'est pas adoptable.");
        }
        if (req.equipeId() != null) {
            equipeRepository.findById(req.equipeId())
                    .filter(e -> clubId.equals(e.getClubId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Équipe introuvable"));
        }
        ClubReferentiel a = (req.equipeId() == null
                ? adoptionRepository.findByClubIdAndEquipeIdIsNull(clubId)
                : adoptionRepository.findByClubIdAndEquipeId(clubId, req.equipeId()))
                .orElseGet(ClubReferentiel::new);
        a.setClubId(clubId);
        a.setEquipeId(req.equipeId());
        a.setReferentielId(r.getId());
        a.setAdoptePar(currentUser.current().getId());
        a.setUpdatedAt(LocalDateTime.now());
        adoptionRepository.save(a);
        ReferentielObjectif plusRecent = versionPlusRecente(r);
        return new AdoptionDto(a.getId(), a.getEquipeId(),
                a.getEquipeId() == null ? "Tout le club"
                        : equipeRepository.findById(a.getEquipeId()).map(e -> e.getNom()).orElse("Équipe"),
                r.getId(), r.getNom(), r.getVersion(),
                plusRecent == null ? null : plusRecent.getId(),
                plusRecent == null ? null : plusRecent.getNom());
    }

    /** Retire une adoption : l'équipe (ou le club) retombe sur le niveau au-dessus, ou sur rien. */
    @Transactional
    public void retirerAdoption(UUID id) {
        exigeModule();
        UUID clubId = clubActif();
        ClubReferentiel a = adoptionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Adoption introuvable"));
        if (!clubId.equals(a.getClubId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Adoption introuvable");
        }
        adoptionRepository.delete(a);
    }

    /**
     * Le référentiel réellement appliqué à une équipe : surcharge d'équipe, sinon défaut du club,
     * sinon rien. « Rien » est une réponse légitime — un club qui n'a rien adopté ne doit voir
     * aucune colonne « Attendu », jamais une valeur inventée.
     */
    @Transactional(readOnly = true)
    public Optional<ReferentielObjectif> resoudre(UUID clubId, UUID equipeId) {
        if (clubId == null) return Optional.empty();
        Optional<ClubReferentiel> adoption = equipeId == null
                ? Optional.empty()
                : adoptionRepository.findByClubIdAndEquipeId(clubId, equipeId);
        if (adoption.isEmpty()) {
            adoption = adoptionRepository.findByClubIdAndEquipeIdIsNull(clubId);
        }
        return adoption.flatMap(a -> referentielRepository.findById(a.getReferentielId()));
    }

    /** Version publique de {@link #resoudre}, pour l'écran d'adoption. */
    @Transactional(readOnly = true)
    public ResolutionDto resolution(UUID equipeId) {
        exigeModule();
        UUID clubId = clubActif();
        boolean parEquipe = equipeId != null
                && adoptionRepository.findByClubIdAndEquipeId(clubId, equipeId).isPresent();
        return resoudre(clubId, equipeId)
                .map(r -> new ResolutionDto(equipeId, toResume(r), parEquipe ? "EQUIPE" : "CLUB"))
                .orElseGet(() -> new ResolutionDto(equipeId, null, "AUCUN"));
    }

    // ──────────────────────── Usage du catalogue (super-admin) ────────────────────────

    /**
     * Combien de clubs sont épinglés sur chaque référentiel. Sert à deux choses : voir qui est
     * resté sur une version ancienne, et refuser de se tromper au moment d'archiver.
     */
    @Transactional(readOnly = true)
    public List<UsageDto> usage() {
        Map<UUID, Long> compte = new LinkedHashMap<>();
        for (Object[] ligne : adoptionRepository.usageParReferentiel()) {
            compte.put((UUID) ligne[0], ((Number) ligne[1]).longValue());
        }
        return referentielRepository.findAll().stream()
                .map(r -> new UsageDto(r.getId(), r.getNom(), r.getNiveau(), r.getVersion(),
                        r.getStatut(), compte.getOrDefault(r.getId(), 0L)))
                .sorted(java.util.Comparator.comparingLong(UsageDto::nbClubs).reversed())
                .toList();
    }

    /** Détail des clubs (et équipes) épinglés sur un référentiel. */
    @Transactional(readOnly = true)
    public List<ClubUtilisateurDto> clubsUtilisateurs(UUID referentielId) {
        List<ClubUtilisateurDto> res = new ArrayList<>();
        for (UUID clubId : adoptionRepository.clubsUtilisateurs(referentielId)) {
            String clubNom = clubRepository.findById(clubId).map(c -> c.getNom()).orElse("Club");
            for (ClubReferentiel a : adoptionRepository.findByClubId(clubId)) {
                if (!referentielId.equals(a.getReferentielId())) continue;
                res.add(new ClubUtilisateurDto(clubId, clubNom, a.getEquipeId(),
                        a.getEquipeId() == null ? "Tout le club"
                                : equipeRepository.findById(a.getEquipeId())
                                    .map(e -> e.getNom()).orElse("Équipe")));
            }
        }
        return res;
    }

    // ──────────────────────── Écart entre deux référentiels ────────────────────────

    /**
     * Diff case par case — sert deux écrans : l'écart d'une copie de club à son standard, et
     * l'aperçu d'une migration de version. Ne renvoie QUE les cases qui diffèrent.
     */
    @Transactional(readOnly = true)
    public EcartResponse ecart(UUID avantId, UUID apresId) {
        ReferentielObjectif avant = exigeReferentiel(avantId);
        ReferentielObjectif apres = exigeReferentiel(apresId);
        exigeLisible(avant);
        exigeLisible(apres);
        Map<String, ReferentielObjectifValeur> a = indexer(avantId);
        Map<String, ReferentielObjectifValeur> b = indexer(apresId);
        List<EcartLigne> lignes = new ArrayList<>();
        for (String cle : union(a.keySet(), b.keySet())) {
            ReferentielObjectifValeur va = a.get(cle);
            ReferentielObjectifValeur vb = b.get(cle);
            Integer aMin = va == null ? null : va.getValeurMin();
            Integer aMax = va == null ? null : va.getValeurMax();
            Integer bMin = vb == null ? null : vb.getValeurMin();
            Integer bMax = vb == null ? null : vb.getValeurMax();
            if (java.util.Objects.equals(aMin, bMin) && java.util.Objects.equals(aMax, bMax)) continue;
            String[] p = cle.split("\\|", 3);
            lignes.add(new EcartLigne(p[0], p[1], p[2], aMin, aMax, bMin, bMax));
        }
        return new EcartResponse(avantId, avant.getNom(), apresId, apres.getNom(), lignes);
    }

    // ──────────────────────── Helpers ────────────────────────

    /** Ordre stable d'affichage : poste, puis contexte, puis ordre métier de la métrique. */
    private static final java.util.Comparator<ValeurDto> ORDRE_AFFICHAGE =
            java.util.Comparator
                    .<ValeurDto>comparingInt(v -> {
                        PosteReference p = PosteReference.parCode(v.poste());
                        return p == null ? 99 : p.getOrdre();
                    })
                    .thenComparing(ValeurDto::contexte)
                    .thenComparingInt(v -> {
                        MetriqueCharge m = MetriqueCharge.parCode(v.metrique());
                        return m == null ? 99 : m.getOrdre();
                    });

    private void remplacerValeurs(ReferentielObjectif r, List<ValeurDto> valeurs) {
        valeurRepository.deleteByReferentielId(r.getId());
        valeurRepository.flush();   // la contrainte d'unicité doit voir les suppressions d'abord
        List<ReferentielObjectifValeur> lignes = new ArrayList<>();
        for (ValeurDto v : valeurs) {
            if (PosteReference.parCode(v.poste()) == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Poste inconnu : " + v.poste());
            }
            if (MetriqueCharge.parCode(v.metrique()) == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Métrique inconnue : " + v.metrique());
            }
            if (!ReferentielObjectifValeur.CONTEXTE_MATCH.equals(v.contexte())
                    && !ReferentielObjectifValeur.CONTEXTE_SEMAINE.equals(v.contexte())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contexte inconnu : " + v.contexte());
            }
            if (v.valeurMin() == null && v.valeurMax() == null) continue;   // case laissée vide
            ReferentielObjectifValeur ligne = new ReferentielObjectifValeur();
            ligne.setReferentielId(r.getId());
            ligne.setPoste(v.poste());
            ligne.setContexte(v.contexte());
            ligne.setMetrique(v.metrique());
            ligne.setValeurMin(v.valeurMin());
            ligne.setValeurMax(v.valeurMax());
            lignes.add(ligne);
        }
        valeurRepository.saveAll(lignes);
        r.setUpdatedAt(LocalDateTime.now());
        referentielRepository.save(r);
    }

    private ReferentielObjectif copier(ReferentielObjectif source, UUID clubId,
                                       String nom, String niveau) {
        ReferentielObjectif copie = new ReferentielObjectif();
        copie.setClubId(clubId);
        copie.setNom(nom);
        copie.setNiveau(niveau);
        copie.setStatut(ReferentielObjectif.STATUT_BROUILLON);
        copie.setCreePar(currentUser.current().getId());
        copie = referentielRepository.save(copie);
        List<ReferentielObjectifValeur> lignes = new ArrayList<>();
        for (ReferentielObjectifValeur v : valeurRepository.findByReferentielId(source.getId())) {
            ReferentielObjectifValeur c = new ReferentielObjectifValeur();
            c.setReferentielId(copie.getId());
            c.setPoste(v.getPoste());
            c.setContexte(v.getContexte());
            c.setMetrique(v.getMetrique());
            c.setValeurMin(v.getValeurMin());
            c.setValeurMax(v.getValeurMax());
            lignes.add(c);
        }
        valeurRepository.saveAll(lignes);
        return copie;
    }

    /** Version publiée plus récente du MÊME niveau plateforme, ou {@code null} s'il n'y en a pas. */
    private ReferentielObjectif versionPlusRecente(ReferentielObjectif r) {
        if (r.getNiveau() == null) return null;
        UUID racine = r.estPlateforme() ? r.getId() : r.getSourceId();
        if (racine == null) return null;
        return referentielRepository.versionsPubliees(r.getNiveau()).stream()
                .filter(c -> c.getVersion() > r.getVersion())
                .filter(c -> !c.getId().equals(r.getId()))
                .findFirst().orElse(null);
    }

    private Map<String, ReferentielObjectifValeur> indexer(UUID referentielId) {
        Map<String, ReferentielObjectifValeur> index = new LinkedHashMap<>();
        for (ReferentielObjectifValeur v : valeurRepository.findByReferentielId(referentielId)) {
            index.put(v.getPoste() + "|" + v.getContexte() + "|" + v.getMetrique(), v);
        }
        return index;
    }

    private static List<String> union(java.util.Set<String> a, java.util.Set<String> b) {
        java.util.Set<String> u = new java.util.LinkedHashSet<>(a);
        u.addAll(b);
        return new ArrayList<>(u);
    }

    private ReferentielResume toResume(ReferentielObjectif r) {
        return new ReferentielResume(r.getId(), r.getClubId(), r.getNom(), r.getNiveau(),
                r.getVersion(), r.getStatut(), r.estPlateforme(), r.estModifiable(),
                r.getSourceId(), r.getParentId(),
                adoptionRepository.countByReferentielId(r.getId()), r.getUpdatedAt());
    }

    private ReferentielObjectif exigeReferentiel(UUID id) {
        if (id == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Référentiel manquant");
        return referentielRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Référentiel introuvable"));
    }

    /** Un club voit les référentiels plateforme et les siens — jamais ceux d'un autre club. */
    private void exigeLisible(ReferentielObjectif r) {
        if (r.estPlateforme()) return;
        Utilisateur u = currentUser.current();
        if (u.getRole() == Role.SUPER_ADMIN) return;
        UUID clubId = permissionResolver.clubActif(u);
        if (clubId == null || !clubId.equals(r.getClubId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Référentiel introuvable");
        }
    }

    /** Un club ne modifie JAMAIS un référentiel plateforme : il en duplique une copie. */
    private void exigeEditable(ReferentielObjectif r) {
        Utilisateur u = currentUser.current();
        if (u.getRole() == Role.SUPER_ADMIN) return;
        if (r.estPlateforme()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Un référentiel de la plateforme ne se modifie pas : dupliquez-le pour l'adapter à votre club.");
        }
        exigeLisible(r);
    }

    private String exigeNom(String nom) {
        if (nom == null || nom.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le nom est obligatoire.");
        }
        return nom.trim();
    }

    private UUID clubActif() {
        UUID clubId = permissionResolver.clubActif(currentUser.current());
        if (clubId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Aucun club actif");
        }
        return clubId;
    }

    /** Double verrou : la permission est vérifiée par la sécurité, le module l'est ici. */
    private void exigeModule() {
        UUID clubId = permissionResolver.clubActif(currentUser.current());
        if (clubId == null) return;   // super-admin hors contexte : rien à verrouiller
        if (!clubModulesService.modulesActifs(clubId).contains(FeatureModule.OBJECTIFS_PERFORMANCE.getCode())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Le module « " + FeatureModule.OBJECTIFS_PERFORMANCE.getLibelle()
                            + " » n'est pas activé pour votre club.");
        }
    }
}
