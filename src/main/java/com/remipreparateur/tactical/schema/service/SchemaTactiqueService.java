package com.remipreparateur.tactical.schema.service;

import com.remipreparateur.tactical.schema.dto.SchemaTactiqueDtos.SchemaRechercheResponse;
import com.remipreparateur.tactical.schema.dto.SchemaTactiqueDtos.SchemaTactiqueRequest;
import com.remipreparateur.tactical.schema.dto.SchemaTactiqueDtos.SchemaTactiqueResponse;
import com.remipreparateur.auth.entity.Role;
import com.remipreparateur.club.entity.Club;
import com.remipreparateur.club.repository.ClubRepository;
import com.remipreparateur.tactical.schema.entity.SchemaTactique;
import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.tactical.schema.repository.SchemaTactiqueRepository;
import com.remipreparateur.auth.repository.UtilisateurRepository;
import com.remipreparateur.shared.security.ContexteActif;
import com.remipreparateur.shared.security.ContexteActifHolder;
import com.remipreparateur.shared.security.CurrentUserProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Schémas tactiques réutilisables (bibliothèque), partagés au sein d'un club.
 * Lecture : tout le staff. Un schéma n'est <b>modifiable/supprimable que par son créateur</b>
 * (pour éviter qu'un coach écrase le travail d'un autre). Les autres peuvent le réutiliser
 * (lecture du contenu) ou le <b>dupliquer</b> pour en obtenir une copie éditable à leur nom.
 *
 * <p><b>Schémas FOURNIS</b> (V64, {@code clubId} null) : posés par le super-admin, ils s'ajoutent
 * à la liste de <i>tous</i> les clubs pour qu'une bibliothèque neuve ne soit pas vide. Un club ne
 * les modifie jamais sur place — il les copie chez lui, et c'est la copie qui lui appartient.
 */
@Service
public class SchemaTactiqueService {

    private final SchemaTactiqueRepository schemaRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ClubRepository clubRepository;
    private final CurrentUserProvider currentUser;

    public SchemaTactiqueService(SchemaTactiqueRepository schemaRepository,
                                 UtilisateurRepository utilisateurRepository,
                                 ClubRepository clubRepository,
                                 CurrentUserProvider currentUser) {
        this.schemaRepository = schemaRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.clubRepository = clubRepository;
        this.currentUser = currentUser;
    }

    /**
     * Bibliothèque visible : les schémas du club <b>plus</b> les schémas fournis (globaux), ces
     * derniers en fin de liste — un coach cherche d'abord son propre travail, le contenu fourni
     * est un point de départ, pas la vitrine.
     */
    public List<SchemaTactiqueResponse> lister() {
        Utilisateur u = currentUser.current();
        UUID clubId = clubCourant(u);
        List<SchemaTactique> schemas = new ArrayList<>();
        if (clubId != null) {
            schemas.addAll(schemaRepository.findByClubIdOrderByUpdatedAtDesc(clubId));
        } else if (u.getRole() == Role.SUPER_ADMIN) {
            // Super-admin sans contexte de club : il administre le contenu fourni.
            return schemaRepository.findByClubIdIsNullOrderByUpdatedAtDesc().stream()
                    .map(s -> toResponse(s, true)).toList();
        }
        schemas.addAll(schemaRepository.findByClubIdIsNullOrderByUpdatedAtDesc());
        return schemas.stream().map(s -> toResponse(s, estModifiable(s, u))).toList();
    }

    /**
     * Bibliothèque GLOBALE (schémas fournis, {@code club_id} NULL) — écran super-admin dédié.
     * Contrairement à {@link #lister()}, ne dépend pas d'un contexte de club actif : le super-admin
     * administre toujours le contenu commun, quel que soit le club sélectionné par ailleurs.
     */
    public List<SchemaTactiqueResponse> listerGlobaux() {
        Utilisateur u = currentUser.current();
        return schemaRepository.findByClubIdIsNullOrderByUpdatedAtDesc().stream()
                .map(s -> toResponse(s, estModifiable(s, u))).toList();
    }

    /** Crée un schéma GLOBAL (fourni, super-admin uniquement) — visible par tous les clubs. */
    public SchemaTactiqueResponse creerGlobal(SchemaTactiqueRequest req) {
        Utilisateur u = currentUser.current();
        if (u.getRole() != Role.SUPER_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé au super-admin");
        }
        SchemaTactique s = new SchemaTactique();
        s.setClubId(null);
        s.setCreePar(u.getId());
        s.setNom(req.nom());
        s.setCategorie(req.categorie());
        s.setSchemaJson(req.schemaJson());
        s.setApercu(req.apercu());
        return toResponse(schemaRepository.save(s), true);
    }

    public SchemaTactiqueResponse creer(SchemaTactiqueRequest req) {
        Utilisateur u = currentUser.current();
        UUID clubId = clubCourant(u);
        // Schéma FOURNI : réservé au super-admin, et volontairement sans club.
        boolean fourni = Boolean.TRUE.equals(req.fourni()) && u.getRole() == Role.SUPER_ADMIN;
        if (clubId == null && !fourni) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Aucun club actif");
        }
        SchemaTactique s = new SchemaTactique();
        s.setClubId(fourni ? null : clubId);
        s.setCreePar(u.getId());
        s.setNom(req.nom());
        s.setCategorie(req.categorie());
        s.setSchemaJson(req.schemaJson());
        s.setApercu(req.apercu());
        return toResponse(schemaRepository.save(s), true);
    }

    public SchemaTactiqueResponse modifier(UUID id, SchemaTactiqueRequest req) {
        Utilisateur u = currentUser.current();
        SchemaTactique s = charge(id);
        exigeModifiable(s, u);
        s.setNom(req.nom());
        s.setCategorie(req.categorie());
        s.setSchemaJson(req.schemaJson());
        s.setApercu(req.apercu());
        s.setUpdatedAt(LocalDateTime.now());
        return toResponse(schemaRepository.save(s), true);
    }

    public void supprimer(UUID id) {
        Utilisateur u = currentUser.current();
        SchemaTactique s = charge(id);
        exigeModifiable(s, u);
        schemaRepository.deleteById(id);
    }

    /**
     * Duplique un schéma en une copie éditable attribuée à l'utilisateur courant. Deux usages :
     * repartir du travail d'un collègue, et <b>copier un schéma FOURNI dans sa bibliothèque</b> —
     * dans ce dernier cas la copie atterrit dans le club de l'appelant, pas dans le global.
     */
    public SchemaTactiqueResponse dupliquer(UUID id) {
        Utilisateur u = currentUser.current();
        SchemaTactique source = charge(id);
        UUID clubId = clubCourant(u);
        boolean sourceFournie = source.getClubId() == null;
        // Un schéma fourni est copiable par tout le monde ; sinon il faut être dans son club.
        if (!sourceFournie && u.getRole() != Role.SUPER_ADMIN
                && (clubId == null || !clubId.equals(source.getClubId()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Schéma introuvable");
        }
        if (sourceFournie && clubId == null && u.getRole() != Role.SUPER_ADMIN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Aucun club actif");
        }
        SchemaTactique copie = new SchemaTactique();
        // La copie d'un schéma fourni appartient au club qui l'a copié (elle cesse d'être globale),
        // sauf pour le super-admin hors contexte de club qui duplique le contenu fourni lui-même.
        copie.setClubId(sourceFournie ? clubId : source.getClubId());
        copie.setCreePar(u.getId());
        copie.setNom(source.getNom() + " (copie)");
        copie.setCategorie(source.getCategorie());
        copie.setSchemaJson(source.getSchemaJson());
        copie.setApercu(source.getApercu());
        return toResponse(schemaRepository.save(copie), true);
    }

    /**
     * Recherche cross-club (super-admin) : parcourt les schémas <b>appartenant aux clubs</b>
     * (jamais les fournis, qui sont déjà globaux), filtrés par nom et/ou catégorie. Sans
     * {@code clubIds} → tous les clubs ; sinon les clubs cochés seulement.
     */
    public List<SchemaRechercheResponse> rechercher(List<UUID> clubIds, String q, String categorie) {
        exigeSuperAdmin();
        List<SchemaTactique> source = (clubIds == null || clubIds.isEmpty())
                ? schemaRepository.findByClubIdIsNotNullOrderByUpdatedAtDesc()
                : schemaRepository.findByClubIdInOrderByUpdatedAtDesc(clubIds);
        String qn = q == null ? "" : q.trim().toLowerCase();
        String cn = categorie == null ? "" : categorie.trim().toLowerCase();
        Map<UUID, String> clubNoms = new HashMap<>();
        return source.stream()
                .filter(s -> qn.isEmpty() || (s.getNom() != null && s.getNom().toLowerCase().contains(qn)))
                .filter(s -> cn.isEmpty() || (s.getCategorie() != null && s.getCategorie().toLowerCase().contains(cn)))
                .map(s -> toRecherche(s, clubNoms))
                .toList();
    }

    /**
     * Promeut un schéma de club en schéma GLOBAL (fourni) : crée une <b>copie</b> sans club, à
     * proposer à tous les clubs. L'original du club n'est <b>jamais</b> modifié ni retiré.
     */
    public SchemaTactiqueResponse promouvoir(UUID id) {
        exigeSuperAdmin();
        SchemaTactique src = charge(id);
        if (src.getClubId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce schéma est déjà global");
        }
        SchemaTactique g = new SchemaTactique();
        g.setClubId(null); // devient global (fourni) ; l'original du club reste intact
        g.setCreePar(currentUser.current().getId());
        g.setNom(src.getNom());
        g.setCategorie(src.getCategorie());
        g.setSchemaJson(src.getSchemaJson());
        g.setApercu(src.getApercu());
        return toResponse(schemaRepository.save(g), true);
    }

    // ── Helpers ──

    private void exigeSuperAdmin() {
        if (currentUser.current().getRole() != Role.SUPER_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé au super-admin");
        }
    }

    /** Nom du club porteur, résolu une fois par club (cache local à la requête). */
    private SchemaRechercheResponse toRecherche(SchemaTactique s, Map<UUID, String> clubNoms) {
        String clubNom = s.getClubId() == null ? null : clubNoms.computeIfAbsent(s.getClubId(),
                cid -> clubRepository.findById(cid).map(Club::getNom).orElse(null));
        String creeParNom = s.getCreePar() != null
                ? utilisateurRepository.findById(s.getCreePar())
                    .map(c -> ((c.getPrenom() != null ? c.getPrenom() + " " : "") + (c.getNom() != null ? c.getNom() : "")).trim())
                    .orElse(null)
                : null;
        return new SchemaRechercheResponse(s.getId(), s.getNom(), s.getCategorie(), s.getApercu(),
                s.getClubId(), clubNom, creeParNom, s.getUpdatedAt());
    }

    private SchemaTactique charge(UUID id) {
        return schemaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schéma introuvable"));
    }

    /** Club courant : club du contexte actif pour le super-admin, sinon club identité. */
    private UUID clubCourant(Utilisateur u) {
        if (u.getRole() == Role.SUPER_ADMIN) {
            ContexteActif ctx = ContexteActifHolder.get();
            return ctx != null ? ctx.clubId() : null;
        }
        return u.getClubId();
    }

    /** Édition/suppression réservées au créateur (le super-admin peut administrer). */
    private void exigeModifiable(SchemaTactique s, Utilisateur u) {
        if (!estModifiable(s, u)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, s.getClubId() == null
                    ? "Ce schéma est fourni — copiez-le dans votre bibliothèque pour l'adapter"
                    : "Seul le créateur peut modifier ce schéma — vous pouvez le dupliquer pour l'adapter");
        }
    }

    /**
     * Modifiable sur place ? Un schéma FOURNI ne l'est que pour le super-admin (c'est du contenu
     * commun à tous les clubs) ; sinon la règle habituelle du créateur s'applique.
     */
    private boolean estModifiable(SchemaTactique s, Utilisateur u) {
        if (u.getRole() == Role.SUPER_ADMIN) return true;
        if (s.getClubId() == null) return false;
        return s.getCreePar() != null && s.getCreePar().equals(u.getId());
    }

    private SchemaTactiqueResponse toResponse(SchemaTactique s, boolean modifiable) {
        String creeParNom = s.getCreePar() != null
                ? utilisateurRepository.findById(s.getCreePar())
                    .map(c -> ((c.getPrenom() != null ? c.getPrenom() + " " : "") + (c.getNom() != null ? c.getNom() : "")).trim())
                    .orElse(null)
                : null;
        return new SchemaTactiqueResponse(s.getId(), s.getNom(), s.getCategorie(), s.getSchemaJson(),
                s.getApercu(), creeParNom, s.getUpdatedAt(), modifiable, s.getClubId() == null);
    }
}
