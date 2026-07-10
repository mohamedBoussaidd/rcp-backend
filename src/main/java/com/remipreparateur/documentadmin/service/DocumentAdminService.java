package com.remipreparateur.documentadmin.service;

import com.remipreparateur.auth.entity.Role;
import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.auth.repository.UtilisateurRepository;
import com.remipreparateur.club.entity.Equipe;
import com.remipreparateur.club.repository.EquipeRepository;
import com.remipreparateur.documentadmin.dto.DocumentAdminDtos.*;
import com.remipreparateur.documentadmin.entity.CategorieAge;
import com.remipreparateur.documentadmin.entity.DocumentJoueur;
import com.remipreparateur.documentadmin.entity.TypeDocumentRequis;
import com.remipreparateur.documentadmin.repository.DocumentJoueurRepository;
import com.remipreparateur.documentadmin.repository.TypeDocumentRequisRepository;
import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.notification.service.NotificationProducer;
import com.remipreparateur.saison.service.AppartenanceService;
import com.remipreparateur.shared.security.CurrentUserProvider;
import com.remipreparateur.shared.security.Scope;
import com.remipreparateur.shared.security.ScopeResolver;
import com.remipreparateur.shared.time.Horloge;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Licences & documents administratifs : référentiel des types requis, conformité de l'effectif,
 * dépôt/validation/refus. Fichier physique hors web-root (app.documentadmin.upload-dir), même
 * pattern que {@code DocumentMedicalService}. Le statut {@code MANQUANT} n'est jamais stocké
 * (absence de ligne) ; un refus/redépôt écrase la ligne existante (pas d'historique).
 */
@Service
public class DocumentAdminService {

    private static final long TAILLE_MAX = 10L * 1024 * 1024; // 10 Mo
    private static final Map<String, String> MIME_EXT = Map.of(
            "application/pdf", "pdf",
            "image/jpeg", "jpg",
            "image/png", "png");
    private static final String MANQUANT = "MANQUANT";
    private static final String SOUMIS = "SOUMIS";
    private static final String VALIDE = "VALIDE";
    private static final String REFUSE = "REFUSE";
    private static final String EXPIRE = "EXPIRE";

    /** Rôles considérés comme « encadrants » pour la conformité documentaire staff. */
    private static final Set<Role> ROLES_STAFF = EnumSet.of(
            Role.PRESIDENT, Role.ENTRAINEUR, Role.PREPARATEUR, Role.MEDICAL, Role.ADMINISTRATIF);

    private final TypeDocumentRequisRepository typeRepository;
    private final DocumentJoueurRepository documentRepository;
    private final CategorieAgeService categorieAgeService;
    private final JoueurRepository joueurRepository;
    private final EquipeRepository equipeRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ScopeResolver scopeResolver;
    private final CurrentUserProvider currentUser;
    private final NotificationProducer notifications;
    private final AppartenanceService appartenance;
    private final Horloge horloge;
    private final Path uploadDir;

    public DocumentAdminService(TypeDocumentRequisRepository typeRepository,
                                DocumentJoueurRepository documentRepository,
                                CategorieAgeService categorieAgeService,
                                JoueurRepository joueurRepository,
                                EquipeRepository equipeRepository,
                                UtilisateurRepository utilisateurRepository,
                                ScopeResolver scopeResolver,
                                CurrentUserProvider currentUser,
                                NotificationProducer notifications,
                                AppartenanceService appartenance,
                                Horloge horloge,
                                @Value("${app.documentadmin.upload-dir}") String uploadDir) {
        this.typeRepository = typeRepository;
        this.documentRepository = documentRepository;
        this.categorieAgeService = categorieAgeService;
        this.joueurRepository = joueurRepository;
        this.equipeRepository = equipeRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.scopeResolver = scopeResolver;
        this.currentUser = currentUser;
        this.notifications = notifications;
        this.appartenance = appartenance;
        this.horloge = horloge;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de créer le dossier de stockage documentadmin : " + this.uploadDir, e);
        }
    }

    public record FichierDocument(Resource resource, String nomOriginal, String typeMime) {}

    // ══════════════════════════ Référentiel (docadmin:configure) ══════════════════════════

    @Transactional(readOnly = true)
    public List<TypeDocumentRequisResponse> listerTypes() {
        UUID clubId = scopeResolver.clubActif();
        return typeRepository.findByClubIdOrderByOrdreAsc(clubId).stream().map(this::toTypeResponse).toList();
    }

    @Transactional
    public TypeDocumentRequisResponse creerType(TypeDocumentRequisRequest req) {
        UUID clubId = scopeResolver.clubActif();
        String code = normaliserCode(req.code());
        if (typeRepository.findByClubIdAndCodeIgnoreCase(clubId, code).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un type « " + code + " » existe déjà");
        }
        TypeDocumentRequis t = new TypeDocumentRequis();
        t.setClubId(clubId);
        t.setCode(code);
        appliquerType(t, req);
        return toTypeResponse(typeRepository.save(t));
    }

    @Transactional
    public TypeDocumentRequisResponse modifierType(UUID id, TypeDocumentRequisRequest req) {
        TypeDocumentRequis t = typeChecke(id);
        appliquerType(t, req);
        return toTypeResponse(typeRepository.save(t));
    }

    private void appliquerType(TypeDocumentRequis t, TypeDocumentRequisRequest req) {
        if (req.libelle() == null || req.libelle().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le libellé est obligatoire");
        }
        t.setLibelle(req.libelle().trim());
        t.setDescription(videEnNull(req.description()));
        t.setObligatoire(req.obligatoire());
        t.setValidationManuelle(req.validationManuelle());
        t.setDureeValiditeMois(req.dureeValiditeMois() != null ? req.dureeValiditeMois().shortValue() : null);
        t.setCategoriesAge(serialiserCategories(t.getClubId(), req.categoriesAge()));
        t.setCible(normaliserCible(req.cible()));
        t.setOrdre(req.ordre() != null ? req.ordre().shortValue() : 0);
        t.setActif(req.actif() == null || req.actif());
    }

    /** Public concerné : JOUEUR (défaut) | STAFF | TOUS. */
    private String normaliserCible(String cible) {
        if (cible == null) return "JOUEUR";
        String c = cible.trim().toUpperCase();
        return switch (c) {
            case "STAFF", "TOUS", "JOUEUR" -> c;
            default -> "JOUEUR";
        };
    }

    private String serialiserCategories(UUID clubId, List<String> codes) {
        if (codes == null || codes.isEmpty()) return null;
        Set<String> connus = categorieAgeService.listerCodesConnus(clubId);
        List<String> valides = codes.stream().filter(c -> c != null && !c.isBlank())
                .map(c -> c.trim().toUpperCase()).filter(connus::contains).distinct().toList();
        return valides.isEmpty() ? null : String.join(",", valides);
    }

    // ══════════════════════════ Matrice de conformité (docadmin:read) ══════════════════════════

    @Transactional(readOnly = true)
    public ConformiteResponse matrice(UUID equipeId) {
        UUID clubId = scopeResolver.clubActif();
        List<Joueur> joueurs = joueursDuPerimetre(equipeId, clubId);
        List<TypeDocumentRequis> types = typeRepository.findByClubIdAndActifTrueOrderByOrdreAsc(clubId);
        Map<UUID, List<DocumentJoueur>> parJoueur = documentsParJoueur(joueurs);
        LocalDate today = horloge.today();

        int complets = 0, incomplets = 0, aValider = 0, expirentSous30j = 0;
        List<JoueurConformite> lignes = new ArrayList<>();
        for (Joueur j : joueurs) {
            Conformite c = conformiteDe(j, clubId, types, parJoueur, today);
            if (c.complet()) complets++; else incomplets++;
            aValider += (int) c.documents().stream().filter(d -> SOUMIS.equals(d.statut())).count();
            expirentSous30j += (int) c.documents().stream()
                    .filter(d -> VALIDE.equals(d.statut()) && d.dateExpiration() != null
                            && !d.dateExpiration().isAfter(today.plusDays(30))).count();
            lignes.add(new JoueurConformite(j.getId(), j.getNom(), j.getPrenom(),
                    c.categorie() != null ? c.categorie().getCode() : null, c.documents()));
        }
        return new ConformiteResponse(lignes, complets, incomplets, aValider, expirentSous30j);
    }

    /**
     * Conformité documentaire du STAFF du club : une ligne par membre du staff (compte encadrant
     * relié à une fiche), colonnes = types de documents ciblant Staff / Tous. Pas de filtre d'âge.
     */
    @Transactional(readOnly = true)
    public ConformiteResponse matriceStaff() {
        UUID clubId = scopeResolver.clubActif();
        List<Utilisateur> comptes = utilisateurRepository.findByClubId(clubId).stream()
                .filter(u -> ROLES_STAFF.contains(u.getRole()) && u.getJoueurId() != null)
                .toList();
        List<Joueur> fiches = joueurRepository.findAllById(
                comptes.stream().map(Utilisateur::getJoueurId).distinct().toList());
        List<TypeDocumentRequis> types = typeRepository.findByClubIdAndActifTrueOrderByOrdreAsc(clubId).stream()
                .filter(t -> "STAFF".equals(t.getCible()) || "TOUS".equals(t.getCible()))
                .toList();
        Map<UUID, List<DocumentJoueur>> parJoueur = documentsParJoueur(fiches);
        LocalDate today = horloge.today();

        int complets = 0, incomplets = 0, aValider = 0, expirentSous30j = 0;
        List<JoueurConformite> lignes = new ArrayList<>();
        for (Joueur j : fiches) {
            List<StatutDocument> statuts = new ArrayList<>();
            Map<UUID, DocumentJoueur> docs = parJoueur.getOrDefault(j.getId(), List.of()).stream()
                    .collect(Collectors.toMap(DocumentJoueur::getTypeDocumentRequisId, Function.identity()));
            boolean complet = true;
            for (TypeDocumentRequis t : types) {
                DocumentJoueur d = docs.get(t.getId());
                String statut = d != null ? d.getStatut() : MANQUANT;
                statuts.add(new StatutDocument(t.getId(), t.getCode(), t.getLibelle(), t.isObligatoire(),
                        d != null ? d.getId() : null, statut,
                        d != null ? d.getDateExpiration() : null, d != null ? d.getMotifRefus() : null));
                if (t.isObligatoire() && !VALIDE.equals(statut)) complet = false;
            }
            if (complet) complets++; else incomplets++;
            aValider += (int) statuts.stream().filter(x -> SOUMIS.equals(x.statut())).count();
            expirentSous30j += (int) statuts.stream()
                    .filter(x -> VALIDE.equals(x.statut()) && x.dateExpiration() != null
                            && !x.dateExpiration().isAfter(today.plusDays(30))).count();
            lignes.add(new JoueurConformite(j.getId(), j.getNom(), j.getPrenom(), null, statuts));
        }
        return new ConformiteResponse(lignes, complets, incomplets, aValider, expirentSous30j);
    }

    private record Conformite(boolean complet, List<StatutDocument> documents, CategorieAge categorie) {}

    /** Calcul de conformité d'un joueur, factorisé pour la matrice ET les tâches planifiées. */
    private Conformite conformiteDe(Joueur joueur, UUID clubId, List<TypeDocumentRequis> types,
                                    Map<UUID, List<DocumentJoueur>> parJoueur, LocalDate today) {
        CategorieAge cat = categorieAgeService.calculerPour(joueur, clubId);
        List<TypeDocumentRequis> applicables = types.stream().filter(t -> applicable(t, cat)).toList();
        Map<UUID, DocumentJoueur> docs = parJoueur.getOrDefault(joueur.getId(), List.of()).stream()
                .collect(Collectors.toMap(DocumentJoueur::getTypeDocumentRequisId, Function.identity()));
        boolean complet = true;
        List<StatutDocument> statuts = new ArrayList<>();
        for (TypeDocumentRequis t : applicables) {
            DocumentJoueur d = docs.get(t.getId());
            String statut = d != null ? d.getStatut() : MANQUANT;
            statuts.add(new StatutDocument(t.getId(), t.getCode(), t.getLibelle(), t.isObligatoire(),
                    d != null ? d.getId() : null, statut,
                    d != null ? d.getDateExpiration() : null, d != null ? d.getMotifRefus() : null));
            if (t.isObligatoire() && !VALIDE.equals(statut)) complet = false;
        }
        return new Conformite(complet, statuts, cat);
    }

    private boolean applicable(TypeDocumentRequis t, CategorieAge cat) {
        // Vues JOUEUR (matrice / PWA / relances) : on exclut les documents ciblant uniquement le
        // STAFF. Les types 'JOUEUR' et 'TOUS' s'appliquent aux joueurs (filtrés ensuite par âge).
        if ("STAFF".equals(t.getCible())) return false;
        if (t.getCategoriesAge() == null || t.getCategoriesAge().isBlank()) return true;
        if (cat == null) return false;
        return Arrays.asList(t.getCategoriesAge().split(",")).contains(cat.getCode());
    }

    private List<Joueur> joueursDuPerimetre(UUID equipeId, UUID clubId) {
        if (equipeId != null) {
            scopeResolver.verifieAcces(equipeId);
            return joueurRepository.findByEquipeIdIn(List.of(equipeId));
        }
        Scope s = scopeResolver.resolve();
        if (s.all()) {
            List<UUID> equipeIds = equipeRepository.findByClubId(clubId).stream().map(Equipe::getId).toList();
            return joueurRepository.findByEquipeIdIn(equipeIds);
        }
        if (s.none()) return List.of();
        return joueurRepository.findByEquipeIdIn(s.equipeIds());
    }

    private Map<UUID, List<DocumentJoueur>> documentsParJoueur(List<Joueur> joueurs) {
        if (joueurs.isEmpty()) return Map.of();
        return documentRepository.findByJoueurIdIn(joueurs.stream().map(Joueur::getId).toList())
                .stream().collect(Collectors.groupingBy(DocumentJoueur::getJoueurId));
    }

    // ══════════════════════════ Upload / validation staff ══════════════════════════

    @Transactional
    public DocumentResponse deposerParStaff(UUID joueurId, UUID typeId, MultipartFile fichier) {
        Joueur joueur = joueurChecke(joueurId);
        TypeDocumentRequis type = typeChecke(typeId);
        DocumentJoueur d = enregistrerFichier(joueur, type, fichier);
        d.setStatut(VALIDE);
        d.setDateValidation(LocalDateTime.now());
        d.setValidePar(currentUser.current().getId());
        d.setDateExpiration(calculerExpiration(type));
        d.setMotifRefus(null);
        d.setUpdatedAt(LocalDateTime.now());
        return toDocumentResponse(documentRepository.save(d));
    }

    @Transactional
    public DocumentResponse valider(UUID documentId) {
        DocumentJoueur d = documentChecke(documentId);
        TypeDocumentRequis type = typeRepository.findById(d.getTypeDocumentRequisId()).orElse(null);
        d.setStatut(VALIDE);
        d.setDateValidation(LocalDateTime.now());
        d.setValidePar(currentUser.current().getId());
        d.setDateExpiration(type != null ? calculerExpiration(type) : null);
        d.setMotifRefus(null);
        d.setUpdatedAt(LocalDateTime.now());
        DocumentJoueur saved = documentRepository.save(d);
        notifierStatut(saved, type, true, null);
        return toDocumentResponse(saved);
    }

    @Transactional
    public DocumentResponse refuser(UUID documentId, String motif) {
        if (motif == null || motif.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le motif de refus est obligatoire");
        }
        DocumentJoueur d = documentChecke(documentId);
        TypeDocumentRequis type = typeRepository.findById(d.getTypeDocumentRequisId()).orElse(null);
        d.setStatut(REFUSE);
        d.setMotifRefus(motif.trim());
        d.setDateValidation(null);
        d.setDateExpiration(null);
        d.setUpdatedAt(LocalDateTime.now());
        DocumentJoueur saved = documentRepository.save(d);
        notifierStatut(saved, type, false, motif.trim());
        return toDocumentResponse(saved);
    }

    private void notifierStatut(DocumentJoueur d, TypeDocumentRequis type, boolean valide, String motif) {
        Joueur joueur = joueurRepository.findById(d.getJoueurId()).orElse(null);
        if (joueur != null) {
            notifications.documentAdminStatutChange(appartenance.equipePrincipale(joueur.getId()), joueur.getId(),
                    type != null ? type.getLibelle() : "Document", valide, motif);
        }
    }

    private LocalDate calculerExpiration(TypeDocumentRequis type) {
        return type.getDureeValiditeMois() == null ? null : horloge.today().plusMonths(type.getDureeValiditeMois());
    }

    // ══════════════════════════ Téléchargement ══════════════════════════

    @Transactional(readOnly = true)
    public FichierDocument chargerPourStaff(UUID documentId) {
        return charger(documentChecke(documentId));
    }

    @Transactional(readOnly = true)
    public FichierDocument chargerPourJoueur(UUID joueurId, UUID documentId) {
        DocumentJoueur d = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable"));
        if (!d.getJoueurId().equals(joueurId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable");
        }
        return charger(d);
    }

    private FichierDocument charger(DocumentJoueur d) {
        if (d.getCheminStockage() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun fichier pour ce document");
        }
        Path p = uploadDir.resolve(d.getCheminStockage()).normalize();
        if (!p.startsWith(uploadDir)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable");
        }
        try {
            Resource r = new UrlResource(p.toUri());
            if (!r.exists() || !r.isReadable()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichier introuvable sur le serveur");
            }
            return new FichierDocument(r, d.getNomOriginal(), d.getTypeMime());
        } catch (MalformedURLException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Chemin de fichier invalide", e);
        }
    }

    // ══════════════════════════ Espace joueur (/api/moi) ══════════════════════════

    @Transactional(readOnly = true)
    public List<MonDocumentResponse> mesDocuments(UUID joueurId) {
        Joueur joueur = joueurRepository.findById(joueurId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fiche joueur introuvable"));
        UUID clubId = clubDuJoueur(joueur);
        CategorieAge cat = categorieAgeService.calculerPour(joueur, clubId);
        List<TypeDocumentRequis> types = typeRepository.findByClubIdAndActifTrueOrderByOrdreAsc(clubId).stream()
                .filter(t -> applicable(t, cat)).toList();
        Map<UUID, DocumentJoueur> docs = documentRepository.findByJoueurIdIn(List.of(joueurId)).stream()
                .collect(Collectors.toMap(DocumentJoueur::getTypeDocumentRequisId, Function.identity()));
        return types.stream().map(t -> {
            DocumentJoueur d = docs.get(t.getId());
            return new MonDocumentResponse(t.getId(), t.getLibelle(), t.getDescription(), t.isObligatoire(),
                    d != null ? d.getId() : null, d != null ? d.getStatut() : MANQUANT,
                    d != null ? d.getNomOriginal() : null,
                    d != null ? d.getDateExpiration() : null, d != null ? d.getMotifRefus() : null);
        }).toList();
    }

    @Transactional
    public MonDocumentResponse deposerParJoueur(UUID joueurId, UUID typeId, MultipartFile fichier) {
        Joueur joueur = joueurRepository.findById(joueurId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fiche joueur introuvable"));
        TypeDocumentRequis type = typeRepository.findById(typeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Type de document introuvable"));
        if (!type.getClubId().equals(clubDuJoueur(joueur))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Type de document introuvable");
        }
        DocumentJoueur d = enregistrerFichier(joueur, type, fichier);
        boolean auto = !type.isValidationManuelle();
        d.setStatut(auto ? VALIDE : SOUMIS);
        d.setMotifRefus(null);
        d.setDateValidation(auto ? LocalDateTime.now() : null);
        d.setDateExpiration(auto ? calculerExpiration(type) : null);
        d.setUpdatedAt(LocalDateTime.now());
        DocumentJoueur saved = documentRepository.save(d);
        return new MonDocumentResponse(type.getId(), type.getLibelle(), type.getDescription(), type.isObligatoire(),
                saved.getId(), saved.getStatut(), saved.getNomOriginal(), saved.getDateExpiration(), null);
    }

    // ══════════════════════════ Scheduler (hors contexte HTTP) ══════════════════════════

    /** Documents VALIDE dont l'expiration est dépassée → EXPIRE (heure réelle, jamais l'Horloge simulée). */
    @Transactional
    public List<DocumentJoueur> expirerDepasses() {
        List<DocumentJoueur> expires = documentRepository.findByStatutAndDateExpirationBefore(VALIDE, LocalDate.now());
        for (DocumentJoueur d : expires) {
            d.setStatut(EXPIRE);
            d.setUpdatedAt(LocalDateTime.now());
        }
        return documentRepository.saveAll(expires);
    }

    public record ResumeConformiteClub(int incomplets, int aValider, int expirentSous30j) {}

    /** Résumé de conformité d'un club, pour le digest hebdomadaire staff (Président/Administratif). */
    @Transactional(readOnly = true)
    public ResumeConformiteClub resumeConformite(UUID clubId) {
        List<Joueur> joueurs = joueursDuClub(clubId);
        List<TypeDocumentRequis> types = typeRepository.findByClubIdAndActifTrueOrderByOrdreAsc(clubId);
        Map<UUID, List<DocumentJoueur>> parJoueur = documentsParJoueur(joueurs);
        LocalDate today = LocalDate.now();
        int incomplets = 0, aValider = 0, expirentSous30j = 0;
        for (Joueur j : joueurs) {
            Conformite c = conformiteDe(j, clubId, types, parJoueur, today);
            if (!c.complet()) incomplets++;
            aValider += (int) c.documents().stream().filter(d -> SOUMIS.equals(d.statut())).count();
            expirentSous30j += (int) c.documents().stream()
                    .filter(d -> VALIDE.equals(d.statut()) && d.dateExpiration() != null
                            && !d.dateExpiration().isAfter(today.plusDays(30))).count();
        }
        return new ResumeConformiteClub(incomplets, aValider, expirentSous30j);
    }

    /** Joueurs ayant au moins un document obligatoire MANQUANT ou REFUSE, pour la relance hebdo. */
    @Transactional(readOnly = true)
    public Map<Joueur, Integer> joueursIncomplets(UUID clubId) {
        List<Joueur> joueurs = joueursDuClub(clubId);
        List<TypeDocumentRequis> types = typeRepository.findByClubIdAndActifTrueOrderByOrdreAsc(clubId);
        Map<UUID, List<DocumentJoueur>> parJoueur = documentsParJoueur(joueurs);
        LocalDate today = LocalDate.now();
        Map<Joueur, Integer> resultat = new LinkedHashMap<>();
        for (Joueur j : joueurs) {
            Conformite c = conformiteDe(j, clubId, types, parJoueur, today);
            int manquants = (int) c.documents().stream()
                    .filter(d -> d.obligatoire() && (MANQUANT.equals(d.statut()) || REFUSE.equals(d.statut())))
                    .count();
            if (manquants > 0) resultat.put(j, manquants);
        }
        return resultat;
    }

    private List<Joueur> joueursDuClub(UUID clubId) {
        List<UUID> equipeIds = equipeRepository.findByClubId(clubId).stream().map(Equipe::getId).toList();
        return equipeIds.isEmpty() ? List.of() : joueurRepository.findByEquipeIdIn(equipeIds);
    }

    // ══════════════════════════ Interne ══════════════════════════

    /** Dépôt de fichier (staff ou joueur) : écrasement simple de la ligne (joueur, type) existante. */
    private DocumentJoueur enregistrerFichier(Joueur joueur, TypeDocumentRequis type, MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fichier manquant");
        }
        if (fichier.getSize() > TAILLE_MAX) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Fichier trop volumineux (max 10 Mo)");
        }
        String mime = fichier.getContentType();
        String ext = mime == null ? null : MIME_EXT.get(mime);
        if (ext == null) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Type de fichier non autorisé (PDF, JPG, PNG)");
        }
        DocumentJoueur d = documentRepository.findByJoueurIdAndTypeDocumentRequisId(joueur.getId(), type.getId())
                .orElseGet(DocumentJoueur::new);
        String ancienChemin = d.getCheminStockage();

        String nomPhysique = UUID.randomUUID() + "." + ext;
        Path cible = uploadDir.resolve(nomPhysique).normalize();
        if (!cible.startsWith(uploadDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chemin de stockage invalide");
        }
        try {
            Files.copy(fichier.getInputStream(), cible, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Échec de l'enregistrement du fichier", e);
        }
        if (ancienChemin != null) {
            try {
                Files.deleteIfExists(uploadDir.resolve(ancienChemin).normalize());
            } catch (IOException ignored) {
                // Le fichier physique peut déjà avoir disparu : on continue quand même.
            }
        }

        d.setClubId(type.getClubId());
        d.setJoueurId(joueur.getId());
        d.setTypeDocumentRequisId(type.getId());
        d.setNomOriginal(nettoyerNom(fichier.getOriginalFilename(), ext));
        d.setTypeMime(mime);
        d.setTailleOctets(fichier.getSize());
        d.setCheminStockage(nomPhysique);
        d.setDateSoumission(LocalDateTime.now());
        return d;
    }

    private Joueur joueurChecke(UUID joueurId) {
        Joueur j = joueurRepository.findById(joueurId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Joueur introuvable"));
        scopeResolver.verifieAccesPersonne(j.getId(), j.getClubId());
        return j;
    }

    private TypeDocumentRequis typeChecke(UUID id) {
        TypeDocumentRequis t = typeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Type de document introuvable"));
        scopeResolver.verifieAccesClub(t.getClubId());
        return t;
    }

    private DocumentJoueur documentChecke(UUID id) {
        DocumentJoueur d = documentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable"));
        Joueur j = joueurRepository.findById(d.getJoueurId()).orElse(null);
        scopeResolver.verifieAccesPersonne(d.getJoueurId(), j != null ? j.getClubId() : null);
        return d;
    }

    private UUID clubDuJoueur(Joueur joueur) {
        return joueur.getClubId();   // Phase 4 : le club est porté par la fiche (plus de dérivation via l'équipe)
    }

    private String normaliserCode(String code) {
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le code est obligatoire");
        }
        return code.trim().toLowerCase().replaceAll("[^a-z0-9_]+", "_");
    }

    private String nettoyerNom(String nom, String ext) {
        if (nom == null || nom.isBlank()) return "document." + ext;
        String base = nom.replaceAll(".*[/\\\\]", "").trim();
        if (base.isEmpty()) base = "document." + ext;
        return base.length() > 255 ? base.substring(0, 255) : base;
    }

    private String videEnNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private TypeDocumentRequisResponse toTypeResponse(TypeDocumentRequis t) {
        List<String> cats = (t.getCategoriesAge() == null || t.getCategoriesAge().isBlank())
                ? List.of() : Arrays.asList(t.getCategoriesAge().split(","));
        return new TypeDocumentRequisResponse(t.getId(), t.getCode(), t.getLibelle(), t.getDescription(),
                t.isObligatoire(), t.isValidationManuelle(),
                t.getDureeValiditeMois() != null ? t.getDureeValiditeMois().intValue() : null,
                cats, t.getCible(), t.getOrdre(), t.isActif());
    }

    private DocumentResponse toDocumentResponse(DocumentJoueur d) {
        return new DocumentResponse(d.getId(), d.getJoueurId(), d.getTypeDocumentRequisId(), d.getStatut(),
                d.getNomOriginal(), d.getTypeMime(), d.getTailleOctets(), d.getDateSoumission(),
                d.getDateValidation(), d.getMotifRefus(), d.getDateExpiration());
    }
}
