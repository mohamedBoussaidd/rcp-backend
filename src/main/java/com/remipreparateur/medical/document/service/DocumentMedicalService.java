package com.remipreparateur.medical.document.service;

import com.remipreparateur.medical.blessure.entity.Blessure;
import com.remipreparateur.medical.blessure.repository.BlessureRepository;
import com.remipreparateur.medical.document.dto.DocumentMedicalDtos.DocumentMedicalResponse;
import com.remipreparateur.medical.document.entity.DocumentMedical;
import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.medical.document.repository.DocumentMedicalRepository;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.saison.service.AppartenanceService;
import com.remipreparateur.shared.security.CurrentUserProvider;
import com.remipreparateur.shared.security.Scope;
import com.remipreparateur.shared.security.ScopeResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Documents medicaux deposes par le joueur. Fichier physique stocke hors web root
 * (app.medical.upload-dir), metadonnees en base.
 *
 * Visibilite : toujours le joueur proprietaire + MEDICAL + SUPER_ADMIN ; en plus,
 * les roles listes dans {@code partageRoles} (choisis par le joueur).
 */
@Service
public class DocumentMedicalService {

    private static final long TAILLE_MAX = 10L * 1024 * 1024; // 10 Mo
    private static final Set<String> CATEGORIES =
            Set.of("irm", "radio", "echographie", "bilan_sanguin", "certificat", "ordonnance",
                    "arret_travail", "accident_travail", "autre");
    /** Catégories admissibles comme déclaration rattachée à une blessure. */
    private static final Set<String> CATEGORIES_DECLARATION = Set.of("arret_travail", "accident_travail");
    /** Rôles voyant les déclarations en plus du médical (cf. blessures:qualify). */
    private static final String PARTAGE_DECLARATION = "PRESIDENT,ADMINISTRATIF";
    /** Roles que le joueur peut ajouter au partage (le medical/super-admin voient toujours). */
    private static final Set<String> ROLES_PARTAGEABLES =
            Set.of("ENTRAINEUR", "PREPARATEUR", "PRESIDENT");
    /** MIME autorise -> extension physique. */
    private static final Map<String, String> MIME_EXT = Map.of(
            "application/pdf", "pdf",
            "image/jpeg", "jpg",
            "image/png", "png");

    private final DocumentMedicalRepository repository;
    private final JoueurRepository joueurRepository;
    private final BlessureRepository blessureRepository;
    private final ScopeResolver scopeResolver;
    private final CurrentUserProvider currentUser;
    private final AppartenanceService appartenance;
    private final Path uploadDir;

    public DocumentMedicalService(DocumentMedicalRepository repository,
                                  JoueurRepository joueurRepository,
                                  BlessureRepository blessureRepository,
                                  ScopeResolver scopeResolver,
                                  CurrentUserProvider currentUser,
                                  AppartenanceService appartenance,
                                  @Value("${app.medical.upload-dir}") String uploadDir) {
        this.repository = repository;
        this.joueurRepository = joueurRepository;
        this.blessureRepository = blessureRepository;
        this.scopeResolver = scopeResolver;
        this.currentUser = currentUser;
        this.appartenance = appartenance;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de creer le dossier de stockage medical : " + this.uploadDir, e);
        }
    }

    /** Fichier pret au telechargement (resource + nom d'origine + type MIME). */
    public record FichierDocument(Resource resource, String nomOriginal, String typeMime) {}

    /** Resultat du stockage physique d'un upload. */
    private record Stockage(String nomPhysique, String mime, String ext) {}

    /** Valide (taille, MIME) et ecrit le fichier sous un nom genere (anti-traversal). */
    private Stockage stockerFichier(MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fichier manquant");
        }
        if (fichier.getSize() > TAILLE_MAX) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Fichier trop volumineux (max 10 Mo)");
        }
        String mime = fichier.getContentType();
        String ext = mime == null ? null : MIME_EXT.get(mime);
        if (ext == null) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Type de fichier non autorise (PDF, JPG, PNG)");
        }
        String nomPhysique = UUID.randomUUID() + "." + ext;
        Path cible = uploadDir.resolve(nomPhysique).normalize();
        if (!cible.startsWith(uploadDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chemin de stockage invalide");
        }
        try {
            Files.copy(fichier.getInputStream(), cible, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Echec de l'enregistrement du fichier", e);
        }
        return new Stockage(nomPhysique, mime, ext);
    }

    // ──────────────────────────── Joueur ────────────────────────────

    public DocumentMedicalResponse deposer(UUID joueurId, MultipartFile fichier,
                                           String categorie, String description,
                                           List<String> partageRoles) {
        String cat = normaliserCategorie(categorie);

        Joueur joueur = joueurRepository.findById(joueurId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Fiche joueur introuvable"));

        Stockage s = stockerFichier(fichier);

        DocumentMedical d = new DocumentMedical();
        d.setJoueurId(joueurId);
        d.setEquipeId(appartenance.equipePrincipale(joueurId));
        d.setNomOriginal(nettoyerNom(fichier.getOriginalFilename(), s.ext()));
        d.setTypeMime(s.mime());
        d.setTailleOctets(fichier.getSize());
        d.setCheminStockage(s.nomPhysique());
        d.setCategorie(cat);
        d.setDescription(videEnNull(description));
        d.setPartageRoles(serialiserRoles(partageRoles));
        d.setDeposePar(currentUser.current().getId());
        return toResponse(repository.save(d), joueur);
    }

    public List<DocumentMedicalResponse> listerPourJoueur(UUID joueurId) {
        List<DocumentMedical> docs = repository.findByJoueurIdOrderByDateDepotDesc(joueurId);
        Joueur joueur = joueurRepository.findById(joueurId).orElse(null);
        return docs.stream().map(d -> toResponse(d, joueur)).toList();
    }

    public FichierDocument chargerPourJoueur(UUID joueurId, UUID id) {
        DocumentMedical d = getOuErreur(id);
        if (!d.getJoueurId().equals(joueurId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable");
        }
        return charger(d);
    }

    public DocumentMedicalResponse modifierPartage(UUID joueurId, UUID id, List<String> partageRoles) {
        DocumentMedical d = getOuErreur(id);
        if (!d.getJoueurId().equals(joueurId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable");
        }
        d.setPartageRoles(serialiserRoles(partageRoles));
        Joueur joueur = joueurRepository.findById(d.getJoueurId()).orElse(null);
        return toResponse(repository.save(d), joueur);
    }

    public void supprimerParJoueur(UUID joueurId, UUID id) {
        DocumentMedical d = getOuErreur(id);
        if (!d.getJoueurId().equals(joueurId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable");
        }
        supprimerPhysiqueEtBase(d);
    }

    // ──────────────────────────── Staff ────────────────────────────

    public List<DocumentMedicalResponse> listerPourStaff(UUID joueurId) {
        List<DocumentMedical> docs;
        if (joueurId != null) {
            docs = repository.findByJoueurIdOrderByDateDepotDesc(joueurId).stream()
                    .filter(d -> scopeResolver.peutAcceder(d.getEquipeId()))
                    .toList();
        } else {
            Scope s = scopeResolver.resolve();
            if (s.all()) docs = repository.findAllByOrderByDateDepotDesc();
            else if (s.none()) docs = List.of();
            else docs = repository.findByEquipeIdInOrderByDateDepotDesc(s.equipeIds());
        }
        String role = currentUser.current().getRole().name();
        List<DocumentMedical> visibles = docs.stream().filter(d -> peutVoir(role, d)).toList();

        Map<UUID, Joueur> joueurs = joueurRepository.findAllById(
                        visibles.stream().map(DocumentMedical::getJoueurId).distinct().toList())
                .stream().collect(Collectors.toMap(Joueur::getId, Function.identity()));
        return visibles.stream().map(d -> toResponse(d, joueurs.get(d.getJoueurId()))).toList();
    }

    public FichierDocument chargerPourStaff(UUID id) {
        DocumentMedical d = getOuErreur(id);
        scopeResolver.verifieAcces(d.getEquipeId());
        if (!peutVoir(currentUser.current().getRole().name(), d)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable");
        }
        return charger(d);
    }

    /** Suppression staff : reservee MEDICAL/SUPER_ADMIN (controle dans SecurityConfig). */
    public void supprimerParStaff(UUID id) {
        DocumentMedical d = getOuErreur(id);
        scopeResolver.verifieAcces(d.getEquipeId());
        supprimerPhysiqueEtBase(d);
    }

    // ──────────────────── Déclarations (arrêt / accident de travail) ────────────────────
    // Rattachées à une blessure, accessibles via /api/blessures/{id}/declarations, gardées par
    // blessures:qualify (MEDICAL + PRESIDENT + ADMINISTRATIF — cf. SecurityConfig).

    public DocumentMedicalResponse deposerDeclaration(UUID blessureId, MultipartFile fichier,
                                                      String categorie, String description) {
        Blessure b = blessureChecke(blessureId);
        String cat = categorie == null ? "" : categorie.trim().toLowerCase();
        if (!CATEGORIES_DECLARATION.contains(cat)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Categorie de declaration invalide (arret_travail | accident_travail)");
        }
        Joueur joueur = joueurRepository.findById(b.getJoueurId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Fiche joueur introuvable"));

        Stockage s = stockerFichier(fichier);

        DocumentMedical d = new DocumentMedical();
        d.setJoueurId(b.getJoueurId());
        d.setEquipeId(b.getEquipeId());
        d.setBlessureId(blessureId);
        d.setNomOriginal(nettoyerNom(fichier.getOriginalFilename(), s.ext()));
        d.setTypeMime(s.mime());
        d.setTailleOctets(fichier.getSize());
        d.setCheminStockage(s.nomPhysique());
        d.setCategorie(cat);
        d.setDescription(videEnNull(description));
        d.setPartageRoles(PARTAGE_DECLARATION);
        d.setDeposePar(currentUser.current().getId());
        return toResponse(repository.save(d), joueur);
    }

    public List<DocumentMedicalResponse> listerDeclarations(UUID blessureId) {
        Blessure b = blessureChecke(blessureId);
        Joueur joueur = joueurRepository.findById(b.getJoueurId()).orElse(null);
        return repository.findByBlessureIdOrderByDateDepotDesc(blessureId).stream()
                .map(d -> toResponse(d, joueur)).toList();
    }

    public FichierDocument chargerDeclaration(UUID blessureId, UUID id) {
        blessureChecke(blessureId);
        return charger(declarationChecke(blessureId, id));
    }

    public void supprimerDeclaration(UUID blessureId, UUID id) {
        blessureChecke(blessureId);
        supprimerPhysiqueEtBase(declarationChecke(blessureId, id));
    }

    private DocumentMedical declarationChecke(UUID blessureId, UUID id) {
        DocumentMedical d = getOuErreur(id);
        if (!blessureId.equals(d.getBlessureId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable");
        }
        return d;
    }

    private Blessure blessureChecke(UUID blessureId) {
        Blessure b = blessureRepository.findById(blessureId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blessure introuvable"));
        scopeResolver.verifieAcces(b.getEquipeId());
        return b;
    }

    // ──────────────────────────── Helpers ────────────────────────────

    private boolean peutVoir(String role, DocumentMedical d) {
        if ("MEDICAL".equals(role) || "SUPER_ADMIN".equals(role)) return true;
        return deserialiserRoles(d.getPartageRoles()).contains(role);
    }

    private FichierDocument charger(DocumentMedical d) {
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

    private void supprimerPhysiqueEtBase(DocumentMedical d) {
        Path p = uploadDir.resolve(d.getCheminStockage()).normalize();
        if (p.startsWith(uploadDir)) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException ignored) {
                // Le fichier physique peut deja avoir disparu : on supprime quand meme la ligne.
            }
        }
        repository.delete(d);
    }

    private DocumentMedical getOuErreur(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable"));
    }

    private String normaliserCategorie(String categorie) {
        String c = categorie == null ? "" : categorie.trim().toLowerCase();
        if (!CATEGORIES.contains(c)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categorie invalide");
        }
        return c;
    }

    /** Conserve uniquement le nom de fichier (anti-traversal d'affichage), borne a 255 caracteres. */
    private String nettoyerNom(String nom, String ext) {
        if (nom == null || nom.isBlank()) return "document." + ext;
        String base = nom.replaceAll(".*[/\\\\]", "").trim();
        if (base.isEmpty()) base = "document." + ext;
        return base.length() > 255 ? base.substring(0, 255) : base;
    }

    private String serialiserRoles(List<String> roles) {
        if (roles == null) return null;
        String csv = roles.stream()
                .filter(r -> r != null)
                .map(r -> r.trim().toUpperCase())
                .filter(ROLES_PARTAGEABLES::contains)
                .distinct()
                .collect(Collectors.joining(","));
        return csv.isEmpty() ? null : csv;
    }

    private List<String> deserialiserRoles(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private String videEnNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private DocumentMedicalResponse toResponse(DocumentMedical d, Joueur j) {
        return new DocumentMedicalResponse(
                d.getId(), d.getJoueurId(),
                j != null ? j.getNom() : null,
                j != null ? j.getPrenom() : null,
                d.getBlessureId(),
                d.getNomOriginal(), d.getTypeMime(), d.getTailleOctets(),
                d.getCategorie(), d.getDescription(),
                deserialiserRoles(d.getPartageRoles()),
                d.getDateDepot());
    }
}
