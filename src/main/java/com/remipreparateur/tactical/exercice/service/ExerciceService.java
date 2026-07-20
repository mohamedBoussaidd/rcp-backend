package com.remipreparateur.tactical.exercice.service;

import com.remipreparateur.tactical.exercice.dto.ExerciceDtos.ExerciceAvance;
import com.remipreparateur.tactical.exercice.dto.ExerciceDtos.ExerciceRequest;
import com.remipreparateur.tactical.exercice.dto.ExerciceDtos.ExerciceResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.remipreparateur.club.entity.Equipe;
import com.remipreparateur.tactical.exercice.entity.Exercice;
import com.remipreparateur.auth.entity.Role;
import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.club.repository.EquipeRepository;
import com.remipreparateur.tactical.exercice.repository.ExerciceRepository;
import com.remipreparateur.auth.repository.UtilisateurRepository;
import com.remipreparateur.shared.security.ContexteActif;
import com.remipreparateur.shared.security.ContexteActifHolder;
import com.remipreparateur.shared.security.CurrentUserProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Bibliotheque d'exercices, partagee au sein d'un club. Un exercice n'est
 * <b>modifiable/supprimable que par son créateur</b> (pour éviter qu'un coach écrase le travail
 * d'un autre). Les autres peuvent le réutiliser ou le <b>dupliquer</b> pour en obtenir une copie
 * éditable à leur nom.
 */
@Service
public class ExerciceService {

    private final ExerciceRepository exerciceRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final EquipeRepository equipeRepository;
    private final CurrentUserProvider currentUser;

    public ExerciceService(ExerciceRepository exerciceRepository,
                           UtilisateurRepository utilisateurRepository,
                           EquipeRepository equipeRepository,
                           CurrentUserProvider currentUser) {
        this.exerciceRepository = exerciceRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.equipeRepository = equipeRepository;
        this.currentUser = currentUser;
    }

    public List<ExerciceResponse> lister() {
        Utilisateur u = currentUser.current();
        UUID clubId = clubCourant(u);
        List<Exercice> exercices = (clubId != null)
                ? exerciceRepository.findByClubIdOrderByCreatedAtDesc(clubId)
                // Super-admin sans club actif (espace admin) : tout ; autres rôles sans club : rien.
                : (u.getRole() == Role.SUPER_ADMIN ? exerciceRepository.findAll() : List.of());
        return exercices.stream().map(e -> toResponse(e, estCreateur(e, u))).toList();
    }

    public ExerciceResponse creer(ExerciceRequest req) {
        Utilisateur u = currentUser.current();
        UUID clubId = clubCourant(u);
        if (clubId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Aucun club actif");
        }
        Exercice e = new Exercice();
        e.setClubId(clubId);
        e.setCreePar(u.getId());
        e.setEquipeOrigineId(u.getEquipeId());
        appliquer(e, req);
        return toResponse(exerciceRepository.save(e), true);
    }

    public ExerciceResponse modifier(UUID id, ExerciceRequest req) {
        Utilisateur u = currentUser.current();
        Exercice e = charge(id);
        exigeCreateur(e, u);
        appliquer(e, req);
        return toResponse(exerciceRepository.save(e), true);
    }

    public void supprimer(UUID id) {
        Utilisateur u = currentUser.current();
        Exercice e = charge(id);
        exigeCreateur(e, u);
        exerciceRepository.deleteById(id);
    }

    /** Sauvegarde du schéma tactique (même droit que l'édition de l'exercice : créateur). */
    public ExerciceResponse modifierSchema(UUID id, String schemaJson) {
        Utilisateur u = currentUser.current();
        Exercice e = charge(id);
        exigeCreateur(e, u);
        e.setSchemaJson(schemaJson);
        return toResponse(exerciceRepository.save(e), true);
    }

    /**
     * Duplique un exercice du club en une copie éditable attribuée à l'utilisateur courant.
     * Permet de repartir du travail d'un autre sans modifier l'original.
     */
    public ExerciceResponse dupliquer(UUID id) {
        Utilisateur u = currentUser.current();
        Exercice source = charge(id);
        UUID clubId = clubCourant(u);
        if (u.getRole() != Role.SUPER_ADMIN && (clubId == null || !clubId.equals(source.getClubId()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercice introuvable");
        }
        Exercice c = new Exercice();
        c.setClubId(source.getClubId());
        c.setCreePar(u.getId());
        c.setEquipeOrigineId(u.getEquipeId());
        c.setNom(source.getNom() + " (copie)");
        c.setCategorie(source.getCategorie());
        c.setType(source.getType());
        c.setDureeMinutes(source.getDureeMinutes());
        c.setObjectif(source.getObjectif());
        c.setIntensite(source.getIntensite());
        c.setDescription(source.getDescription());
        c.setSchemaJson(source.getSchemaJson());
        c.setDistanceAttendueM(source.getDistanceAttendueM());
        c.setDistanceHauteIntensiteM(source.getDistanceHauteIntensiteM());
        c.setNbSprints(source.getNbSprints());
        copierAvance(source, c);
        return toResponse(exerciceRepository.save(c), true);
    }

    // ── Helpers ──

    /**
     * Club dont dépend la bibliothèque. Super-admin : le club du contexte actif
     * (null s'il est sur l'espace admin sans club). Autres rôles : leur propre club
     * (le contexte ne peut pas changer leur club).
     */
    private UUID clubCourant(Utilisateur u) {
        if (u.getRole() == Role.SUPER_ADMIN) {
            ContexteActif ctx = ContexteActifHolder.get();
            return ctx != null ? ctx.clubId() : null;
        }
        return u.getClubId();
    }

    private Exercice charge(UUID id) {
        return exerciceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercice introuvable"));
    }

    /** Edition/suppression réservées au créateur (le super-admin peut administrer). */
    private void exigeCreateur(Exercice e, Utilisateur u) {
        if (!estCreateur(e, u)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Seul le créateur peut modifier cet exercice — vous pouvez le dupliquer pour l'adapter");
        }
    }

    private boolean estCreateur(Exercice e, Utilisateur u) {
        return u.getRole() == Role.SUPER_ADMIN || (e.getCreePar() != null && e.getCreePar().equals(u.getId()));
    }

    private void appliquer(Exercice e, ExerciceRequest req) {
        e.setNom(req.nom());
        e.setCategorie(req.categorie());
        String type = req.type() == null ? "TECHNIQUE" : req.type().trim().toUpperCase();
        if (!type.equals("PHYSIQUE") && !type.equals("TECHNIQUE") && !type.equals("MIXTE")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type attendu : PHYSIQUE, TECHNIQUE ou MIXTE");
        }
        e.setType(type);
        e.setDureeMinutes(req.dureeMinutes());
        e.setObjectif(req.objectif());
        if (req.intensite() != null && (req.intensite() < 1 || req.intensite() > 5)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Intensite attendue entre 1 et 5");
        }
        e.setIntensite(req.intensite());
        e.setDescription(req.description());
        e.setDistanceAttendueM(req.distanceAttendueM());
        e.setDistanceHauteIntensiteM(req.distanceHauteIntensiteM());
        e.setNbSprints(req.nbSprints());
        if (req.photoImportId() != null) e.setPhotoImportId(req.photoImportId());
        appliquerAvance(e, req.avance());
    }

    /**
     * Champs du mode avancé : appliqués seulement si le bloc `avance` est présent ET que
     * l'utilisateur détient `seance_avancee:access` (module actif + rôle). Un client en mode
     * simplifié (avance null) ou un club sans le module ne wipe donc jamais les valeurs saisies.
     */
    private void appliquerAvance(Exercice e, ExerciceAvance a) {
        if (a == null || !aAccesAvance()) return;
        e.setContextePedagogique(a.contextePedagogique());
        e.setNiveauObjectif(a.niveauObjectif());
        e.setEchelleEffectif(a.echelleEffectif());
        e.setDominanteTactiqueOrg(a.dominanteTactiqueOrg());
        e.setDominanteTactiqueFonc(a.dominanteTactiqueFonc());
        e.setDominanteMental(a.dominanteMental());
        e.setDominanteTechnique(a.dominanteTechnique());
        e.setDominanteAthletique(a.dominanteAthletique());
        e.setButSystemeMarque(a.butSystemeMarque());
        e.setReglesJeu(a.reglesJeu());
        e.setVariablesPedagogiques(a.variablesPedagogiques());
        e.setReperesPerceptifs(a.reperesPerceptifs());
        e.setComportementsAttendus(a.comportementsAttendus());
        e.setTerrainLongueurM(a.terrainLongueurM());
        e.setTerrainLargeurM(a.terrainLargeurM());
        e.setFormatJoueurs(a.formatJoueurs());
        e.setNbJoueursTotal(a.nbJoueursTotal());
        e.setSequencage(a.sequencage());
    }

    private boolean aAccesAvance() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(g -> "seance_avancee:access".equals(g.getAuthority()));
    }

    private void copierAvance(Exercice source, Exercice cible) {
        cible.setContextePedagogique(source.getContextePedagogique());
        cible.setNiveauObjectif(source.getNiveauObjectif());
        cible.setEchelleEffectif(source.getEchelleEffectif());
        cible.setDominanteTactiqueOrg(source.getDominanteTactiqueOrg());
        cible.setDominanteTactiqueFonc(source.getDominanteTactiqueFonc());
        cible.setDominanteMental(source.getDominanteMental());
        cible.setDominanteTechnique(source.getDominanteTechnique());
        cible.setDominanteAthletique(source.getDominanteAthletique());
        cible.setButSystemeMarque(source.getButSystemeMarque());
        cible.setReglesJeu(source.getReglesJeu());
        cible.setVariablesPedagogiques(source.getVariablesPedagogiques());
        cible.setReperesPerceptifs(source.getReperesPerceptifs());
        cible.setComportementsAttendus(source.getComportementsAttendus());
        cible.setTerrainLongueurM(source.getTerrainLongueurM());
        cible.setTerrainLargeurM(source.getTerrainLargeurM());
        cible.setFormatJoueurs(source.getFormatJoueurs());
        cible.setNbJoueursTotal(source.getNbJoueursTotal());
        cible.setSequencage(source.getSequencage());
    }

    private ExerciceResponse toResponse(Exercice e, boolean modifiable) {
        String creeParNom = e.getCreePar() != null
                ? utilisateurRepository.findById(e.getCreePar())
                    .map(c -> ((c.getPrenom() != null ? c.getPrenom() + " " : "") + (c.getNom() != null ? c.getNom() : "")).trim())
                    .orElse(null)
                : null;
        String equipeNom = e.getEquipeOrigineId() != null
                ? equipeRepository.findById(e.getEquipeOrigineId()).map(Equipe::getNom).orElse(null)
                : null;
        ExerciceAvance avance = new ExerciceAvance(
                e.getContextePedagogique(), e.getNiveauObjectif(), e.getEchelleEffectif(),
                e.getDominanteTactiqueOrg(), e.getDominanteTactiqueFonc(), e.getDominanteMental(),
                e.getDominanteTechnique(), e.getDominanteAthletique(),
                e.getButSystemeMarque(), e.getReglesJeu(), e.getVariablesPedagogiques(),
                e.getReperesPerceptifs(), e.getComportementsAttendus(),
                e.getTerrainLongueurM(), e.getTerrainLargeurM(),
                e.getFormatJoueurs(), e.getNbJoueursTotal(), e.getSequencage());
        return new ExerciceResponse(
                e.getId(), e.getNom(), e.getCategorie(), e.getType(), e.getDureeMinutes(), e.getObjectif(),
                e.getIntensite(), e.getDescription(), e.getSchemaJson(),
                e.getDistanceAttendueM(), e.getDistanceHauteIntensiteM(), e.getNbSprints(),
                e.getCreePar(), creeParNom, e.getEquipeOrigineId(), equipeNom,
                modifiable, avance, e.getPhotoImportId());
    }
}
