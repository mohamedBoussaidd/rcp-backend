package com.remipreparateur.tactical.diaporama.service;

import com.remipreparateur.auth.entity.Role;
import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.auth.rbac.PermissionResolver;
import com.remipreparateur.auth.repository.UtilisateurRepository;
import com.remipreparateur.shared.security.ContexteActif;
import com.remipreparateur.shared.security.ContexteActifHolder;
import com.remipreparateur.shared.security.CurrentUserProvider;
import com.remipreparateur.shared.security.ScopeResolver;
import com.remipreparateur.tactical.diaporama.dto.DiaporamaDtos.*;
import com.remipreparateur.tactical.diaporama.entity.Diaporama;
import com.remipreparateur.tactical.diaporama.entity.Slide;
import com.remipreparateur.tactical.diaporama.repository.DiaporamaRepository;
import com.remipreparateur.tactical.diaporama.repository.SlideRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Diaporamas de séance (supports de présentation réutilisables), niveau club, éventuellement
 * réservés à une équipe ({@code visibilite = EQUIPE}). Lecture : tout le staff (filtrée par
 * périmètre) ; un diaporama n'est <b>modifiable que par son créateur</b> (les autres peuvent le
 * <b>dupliquer</b>). La suppression est ouverte au créateur et au détenteur de {@code diaporama:manage}.
 * Les slides SCHEMA stockent une COPIE du schéma (snapshot), comme partout dans le projet.
 */
@Service
public class DiaporamaService {

    private final DiaporamaRepository diaporamaRepository;
    private final SlideRepository slideRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final CurrentUserProvider currentUser;
    private final ScopeResolver scopeResolver;
    private final PermissionResolver permissionResolver;

    public DiaporamaService(DiaporamaRepository diaporamaRepository,
                            SlideRepository slideRepository,
                            UtilisateurRepository utilisateurRepository,
                            CurrentUserProvider currentUser,
                            ScopeResolver scopeResolver,
                            PermissionResolver permissionResolver) {
        this.diaporamaRepository = diaporamaRepository;
        this.slideRepository = slideRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.currentUser = currentUser;
        this.scopeResolver = scopeResolver;
        this.permissionResolver = permissionResolver;
    }

    /** Diaporamas du club visibles par l'utilisateur : tous les CLUB + les EQUIPE de son périmètre. */
    public List<DiaporamaResume> lister() {
        Utilisateur u = currentUser.current();
        UUID clubId = clubCourant(u);
        List<Diaporama> diapos = (clubId != null)
                ? diaporamaRepository.findByClubIdOrderByUpdatedAtDesc(clubId)
                : (u.getRole() == Role.SUPER_ADMIN ? diaporamaRepository.findAll() : List.of());
        return diapos.stream()
                .filter(d -> visiblePour(d, u))
                .map(d -> toResume(d, u))
                .toList();
    }

    @Transactional
    public DiaporamaDetail creer(DiaporamaCreateRequest req) {
        Utilisateur u = currentUser.current();
        UUID clubId = clubCourant(u);
        if (clubId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Aucun club actif");
        }
        Diaporama d = new Diaporama();
        d.setClubId(clubId);
        d.setCreePar(u.getId());
        d.setTitre(req.titre());
        d.setVisibilite("CLUB");
        d.setStatut("BROUILLON");
        return toDetail(diaporamaRepository.save(d), u);
    }

    public DiaporamaDetail detail(UUID id) {
        Utilisateur u = currentUser.current();
        Diaporama d = chargeAccessible(id, u);
        return toDetail(d, u);
    }

    @Transactional
    public DiaporamaDetail modifier(UUID id, DiaporamaUpdateRequest req) {
        Utilisateur u = currentUser.current();
        Diaporama d = chargeAccessible(id, u);
        exigeCreateur(d, u);
        d.setTitre(req.titre());
        d.setVisibilite(req.visibilite());
        d.setStatut(req.statut());
        // Une visibilité « équipe » doit cibler une équipe active unique ; « club » efface le rattachement.
        d.setEquipeId("EQUIPE".equals(req.visibilite()) ? scopeResolver.equipeActiveUnique() : null);
        d.setUpdatedAt(LocalDateTime.now());
        return toDetail(diaporamaRepository.save(d), u);
    }

    @Transactional
    public void supprimer(UUID id) {
        Utilisateur u = currentUser.current();
        Diaporama d = chargeAccessible(id, u);
        if (!peutSupprimer(d, u)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Seul le créateur (ou un modérateur) peut supprimer ce diaporama");
        }
        slideRepository.deleteByDiaporamaId(d.getId());
        diaporamaRepository.delete(d);
    }

    /** Forke un diaporama accessible en une copie complète (slides incluses) attribuée à l'utilisateur. */
    @Transactional
    public DiaporamaDetail dupliquer(UUID id) {
        Utilisateur u = currentUser.current();
        Diaporama source = chargeAccessible(id, u);
        Diaporama copie = new Diaporama();
        copie.setClubId(source.getClubId());
        copie.setEquipeId(source.getEquipeId());
        copie.setVisibilite(source.getVisibilite());
        copie.setStatut("BROUILLON");
        copie.setCreePar(u.getId());
        copie.setTitre(source.getTitre() + " (copie)");
        copie = diaporamaRepository.save(copie);
        for (Slide s : slideRepository.findByDiaporamaIdOrderByOrdreAsc(source.getId())) {
            Slide c = new Slide();
            c.setDiaporamaId(copie.getId());
            c.setOrdre(s.getOrdre());
            c.setType(s.getType());
            c.setTitre(s.getTitre());
            c.setSchemaJson(s.getSchemaJson());
            c.setApercu(s.getApercu());
            c.setImageSrc(s.getImageSrc());
            c.setVideoUrl(s.getVideoUrl());
            c.setTexte(s.getTexte());
            c.setStyleJson(s.getStyleJson());
            slideRepository.save(c);
        }
        return toDetail(copie, u);
    }

    // ── Slides ──

    @Transactional
    public SlideResponse ajouterSlide(UUID diaporamaId, SlideRequest req) {
        Utilisateur u = currentUser.current();
        Diaporama d = chargeAccessible(diaporamaId, u);
        exigeCreateur(d, u);
        List<Slide> slides = slideRepository.findByDiaporamaIdOrderByOrdreAsc(d.getId());
        int ordre = slides.isEmpty() ? 0 : slides.get(slides.size() - 1).getOrdre() + 1;
        Slide s = new Slide();
        s.setDiaporamaId(d.getId());
        s.setOrdre(ordre);
        appliquer(s, req);
        toucher(d);
        return toSlideResponse(slideRepository.save(s));
    }

    @Transactional
    public SlideResponse modifierSlide(UUID diaporamaId, UUID slideId, SlideRequest req) {
        Utilisateur u = currentUser.current();
        Diaporama d = chargeAccessible(diaporamaId, u);
        exigeCreateur(d, u);
        Slide s = chargeSlide(d, slideId);
        appliquer(s, req);
        s.setUpdatedAt(LocalDateTime.now());
        toucher(d);
        return toSlideResponse(slideRepository.save(s));
    }

    @Transactional
    public void supprimerSlide(UUID diaporamaId, UUID slideId) {
        Utilisateur u = currentUser.current();
        Diaporama d = chargeAccessible(diaporamaId, u);
        exigeCreateur(d, u);
        Slide s = chargeSlide(d, slideId);
        slideRepository.delete(s);
        toucher(d);
    }

    @Transactional
    public DiaporamaDetail reordonner(UUID diaporamaId, ReordonnerRequest req) {
        Utilisateur u = currentUser.current();
        Diaporama d = chargeAccessible(diaporamaId, u);
        exigeCreateur(d, u);
        List<Slide> slides = slideRepository.findByDiaporamaIdOrderByOrdreAsc(d.getId());
        for (Slide s : slides) {
            int idx = req.ordreIds().indexOf(s.getId());
            if (idx < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slide absent de l'ordre fourni");
            }
            s.setOrdre(idx);
        }
        slideRepository.saveAll(slides);
        toucher(d);
        return toDetail(d, u);
    }

    // ── Helpers ──

    /** Reporte le contenu de la requête sur un slide selon son type (les champs non pertinents → null). */
    private void appliquer(Slide s, SlideRequest req) {
        s.setType(req.type());
        s.setTitre(req.titre());
        // Repart d'un slide « vide » : chaque type ne renseigne que ses propres champs.
        s.setSchemaJson(null);
        s.setApercu(null);
        s.setImageSrc(null);
        s.setVideoUrl(null);
        s.setTexte(null);
        s.setStyleJson(null);
        switch (req.type()) {
            case "SCHEMA" -> { s.setSchemaJson(req.schemaJson()); s.setApercu(req.apercu()); }
            case "IMAGE" -> s.setImageSrc(req.imageSrc());
            case "VIDEO_LIEN" -> s.setVideoUrl(req.videoUrl());
            case "TEXTE" -> { s.setTexte(req.texte()); s.setStyleJson(req.styleJson()); }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type de slide inconnu");
        }
    }

    private void toucher(Diaporama d) {
        d.setUpdatedAt(LocalDateTime.now());
        diaporamaRepository.save(d);
    }

    private Slide chargeSlide(Diaporama d, UUID slideId) {
        Slide s = slideRepository.findById(slideId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Slide introuvable"));
        if (!s.getDiaporamaId().equals(d.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Slide introuvable");
        }
        return s;
    }

    /** Charge un diaporama en vérifiant qu'il est dans le périmètre de l'utilisateur (404 sinon). */
    private Diaporama chargeAccessible(UUID id, Utilisateur u) {
        Diaporama d = diaporamaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Diaporama introuvable"));
        if (!visiblePour(d, u)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Diaporama introuvable");
        }
        return d;
    }

    /** Visible si même club que l'utilisateur, et — pour une visibilité équipe — équipe dans son périmètre. */
    private boolean visiblePour(Diaporama d, Utilisateur u) {
        if (u.getRole() == Role.SUPER_ADMIN) return true;
        UUID clubId = clubCourant(u);
        if (clubId == null || !clubId.equals(d.getClubId())) return false;
        if ("EQUIPE".equals(d.getVisibilite())) {
            return scopeResolver.peutAcceder(d.getEquipeId());
        }
        return true;
    }

    /** Club courant : club du contexte actif pour le super-admin, sinon club identité. */
    private UUID clubCourant(Utilisateur u) {
        if (u.getRole() == Role.SUPER_ADMIN) {
            ContexteActif ctx = ContexteActifHolder.get();
            return ctx != null ? ctx.clubId() : null;
        }
        return u.getClubId();
    }

    private void exigeCreateur(Diaporama d, Utilisateur u) {
        if (!estCreateur(d, u)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Seul le créateur peut modifier ce diaporama — vous pouvez le dupliquer pour l'adapter");
        }
    }

    private boolean estCreateur(Diaporama d, Utilisateur u) {
        return u.getRole() == Role.SUPER_ADMIN || (d.getCreePar() != null && d.getCreePar().equals(u.getId()));
    }

    private boolean peutSupprimer(Diaporama d, Utilisateur u) {
        return estCreateur(d, u) || permissionResolver.permissionsPour(u).contains("diaporama:manage");
    }

    private String createurNom(UUID creePar) {
        if (creePar == null) return null;
        return utilisateurRepository.findById(creePar)
                .map(c -> ((c.getPrenom() != null ? c.getPrenom() + " " : "")
                        + (c.getNom() != null ? c.getNom() : "")).trim())
                .filter(s -> !s.isEmpty())
                .orElse(null);
    }

    private DiaporamaResume toResume(Diaporama d, Utilisateur u) {
        List<Slide> slides = slideRepository.findByDiaporamaIdOrderByOrdreAsc(d.getId());
        String apercu = slides.stream()
                .map(s -> s.getApercu() != null ? s.getApercu() : s.getImageSrc())
                .filter(a -> a != null && !a.isEmpty())
                .findFirst().orElse(null);
        return new DiaporamaResume(d.getId(), d.getTitre(), d.getVisibilite(), d.getStatut(),
                createurNom(d.getCreePar()), slides.size(), apercu,
                estCreateur(d, u), peutSupprimer(d, u), d.getUpdatedAt());
    }

    private DiaporamaDetail toDetail(Diaporama d, Utilisateur u) {
        List<SlideResponse> slides = slideRepository.findByDiaporamaIdOrderByOrdreAsc(d.getId())
                .stream().map(this::toSlideResponse).toList();
        return new DiaporamaDetail(d.getId(), d.getTitre(), d.getVisibilite(), d.getStatut(),
                createurNom(d.getCreePar()), estCreateur(d, u), peutSupprimer(d, u), slides);
    }

    private SlideResponse toSlideResponse(Slide s) {
        return new SlideResponse(s.getId(), s.getType(), s.getTitre(), s.getSchemaJson(),
                s.getApercu(), s.getImageSrc(), s.getVideoUrl(), s.getTexte(), s.getStyleJson(), s.getOrdre());
    }
}
