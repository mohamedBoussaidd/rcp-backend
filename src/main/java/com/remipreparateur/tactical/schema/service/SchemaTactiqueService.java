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
import java.util.List;
import java.util.UUID;

/**
 * Schémas tactiques réutilisables (bibliothèque), partagés au sein d'un club.
 * Lecture : tout le staff. Un schéma n'est <b>modifiable/supprimable que par son créateur</b>
 * (pour éviter qu'un coach écrase le travail d'un autre). Les autres peuvent le réutiliser
 * (lecture du contenu) ou le <b>dupliquer</b> pour en obtenir une copie éditable à leur nom.
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

    public List<SchemaTactiqueResponse> lister() {
        Utilisateur u = currentUser.current();
        UUID clubId = clubCourant(u);
        List<SchemaTactique> schemas = (clubId != null)
                ? schemaRepository.findByClubIdOrderByUpdatedAtDesc(clubId)
                : (u.getRole() == Role.SUPER_ADMIN ? schemaRepository.findAll() : List.of());
        return schemas.stream().map(s -> toResponse(s, estCreateur(s, u))).toList();
    }

    public SchemaTactiqueResponse creer(SchemaTactiqueRequest req) {
        Utilisateur u = currentUser.current();
        UUID clubId = clubCourant(u);
        if (clubId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Aucun club actif");
        }
        SchemaTactique s = new SchemaTactique();
        s.setClubId(clubId);
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
        exigeCreateur(s, u);
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
        exigeCreateur(s, u);
        schemaRepository.deleteById(id);
    }

    /**
     * Duplique un schéma existant du club en une nouvelle copie éditable, attribuée à
     * l'utilisateur courant. Permet de repartir du travail d'un autre sans modifier l'original.
     */
    public SchemaTactiqueResponse dupliquer(UUID id) {
        Utilisateur u = currentUser.current();
        SchemaTactique source = charge(id);
        UUID clubId = clubCourant(u);
        // On ne duplique que dans le périmètre du club (l'original doit être accessible).
        if (u.getRole() != Role.SUPER_ADMIN && (clubId == null || !clubId.equals(source.getClubId()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Schéma introuvable");
        }
        SchemaTactique copie = new SchemaTactique();
        copie.setClubId(source.getClubId());
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
    private void exigeCreateur(SchemaTactique s, Utilisateur u) {
        if (!estCreateur(s, u)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Seul le créateur peut modifier ce schéma — vous pouvez le dupliquer pour l'adapter");
        }
    }

    private boolean estCreateur(SchemaTactique s, Utilisateur u) {
        return u.getRole() == Role.SUPER_ADMIN || (s.getCreePar() != null && s.getCreePar().equals(u.getId()));
    }

    private SchemaTactiqueResponse toResponse(SchemaTactique s, boolean modifiable) {
        String creeParNom = s.getCreePar() != null
                ? utilisateurRepository.findById(s.getCreePar())
                    .map(c -> ((c.getPrenom() != null ? c.getPrenom() + " " : "") + (c.getNom() != null ? c.getNom() : "")).trim())
                    .orElse(null)
                : null;
        return new SchemaTactiqueResponse(s.getId(), s.getNom(), s.getCategorie(), s.getSchemaJson(),
                s.getApercu(), creeParNom, s.getUpdatedAt(), modifiable);
    }
}
