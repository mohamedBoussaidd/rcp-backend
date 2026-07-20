package com.remipreparateur.tactical.schema.service;

import com.remipreparateur.tactical.schema.dto.SchemaTactiqueDtos.SchemaTactiqueRequest;
import com.remipreparateur.tactical.schema.dto.SchemaTactiqueDtos.SchemaTactiqueResponse;
import com.remipreparateur.auth.entity.Role;
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
import java.util.List;
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
    private final CurrentUserProvider currentUser;

    public SchemaTactiqueService(SchemaTactiqueRepository schemaRepository,
                                 UtilisateurRepository utilisateurRepository,
                                 CurrentUserProvider currentUser) {
        this.schemaRepository = schemaRepository;
        this.utilisateurRepository = utilisateurRepository;
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

    // ── Helpers ──

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
