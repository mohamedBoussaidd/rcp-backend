package com.remipreparateur.performance.seance.service;

import com.remipreparateur.auth.entity.Role;
import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.auth.repository.UtilisateurRepository;
import com.remipreparateur.club.entity.Equipe;
import com.remipreparateur.club.repository.EquipeRepository;
import com.remipreparateur.performance.seance.dto.SeanceDtos.ExerciceLigne;
import com.remipreparateur.performance.seance.dto.SeanceDtos.ExercicesRequest;
import com.remipreparateur.performance.seance.dto.SeanceDtos.LigneRequest;
import com.remipreparateur.performance.seance.dto.SeanceModeleDtos.*;
import com.remipreparateur.performance.seance.entity.Seance;
import com.remipreparateur.performance.seance.entity.SeanceExercice;
import com.remipreparateur.performance.seance.entity.SeanceModele;
import com.remipreparateur.performance.seance.entity.SeanceModeleExercice;
import com.remipreparateur.performance.seance.entity.TypeSeance;
import com.remipreparateur.performance.seance.repository.SeanceExerciceRepository;
import com.remipreparateur.performance.seance.repository.SeanceModeleExerciceRepository;
import com.remipreparateur.performance.seance.repository.SeanceModeleRepository;
import com.remipreparateur.performance.seance.repository.TypeSeanceRepository;
import com.remipreparateur.tactical.exercice.entity.Exercice;
import com.remipreparateur.tactical.exercice.repository.ExerciceRepository;
import com.remipreparateur.shared.security.ContexteActif;
import com.remipreparateur.shared.security.ContexteActifHolder;
import com.remipreparateur.shared.security.CurrentUserProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Bibliothèque de séances-MODÈLES (espace Coaching), partagée au sein d'un club. Un modèle n'est
 * <b>modifiable/supprimable que par son créateur</b> (comme la bibliothèque d'exercices). Les autres
 * peuvent le <b>dupliquer</b> pour l'adapter, ou le <b>planifier</b> pour en générer une vraie séance
 * dans le calendrier.
 */
@Service
public class SeanceModeleService {

    private final SeanceModeleRepository modeleRepository;
    private final SeanceModeleExerciceRepository modeleExerciceRepository;
    private final SeanceExerciceRepository seanceExerciceRepository;
    private final TypeSeanceRepository typeSeanceRepository;
    private final ExerciceRepository exerciceRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final EquipeRepository equipeRepository;
    private final SeanceService seanceService;
    private final CurrentUserProvider currentUser;

    public SeanceModeleService(SeanceModeleRepository modeleRepository,
                               SeanceModeleExerciceRepository modeleExerciceRepository,
                               SeanceExerciceRepository seanceExerciceRepository,
                               TypeSeanceRepository typeSeanceRepository,
                               ExerciceRepository exerciceRepository,
                               UtilisateurRepository utilisateurRepository,
                               EquipeRepository equipeRepository,
                               SeanceService seanceService,
                               CurrentUserProvider currentUser) {
        this.modeleRepository = modeleRepository;
        this.modeleExerciceRepository = modeleExerciceRepository;
        this.seanceExerciceRepository = seanceExerciceRepository;
        this.typeSeanceRepository = typeSeanceRepository;
        this.exerciceRepository = exerciceRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.equipeRepository = equipeRepository;
        this.seanceService = seanceService;
        this.currentUser = currentUser;
    }

    public List<SeanceModeleResponse> lister() {
        Utilisateur u = currentUser.current();
        UUID clubId = clubCourant(u);
        List<SeanceModele> modeles = (clubId != null)
                ? modeleRepository.findByClubIdOrderByCreatedAtDesc(clubId)
                : (u.getRole() == Role.SUPER_ADMIN ? modeleRepository.findAll() : List.of());
        return modeles.stream().map(m -> toResponse(m, estCreateur(m, u))).toList();
    }

    public SeanceModeleDetail detail(UUID id) {
        Utilisateur u = currentUser.current();
        SeanceModele m = chargeDuClub(id, u);
        return construireDetail(m, estCreateur(m, u));
    }

    public SeanceModeleResponse creer(SeanceModeleRequest req) {
        Utilisateur u = currentUser.current();
        UUID clubId = clubCourant(u);
        if (clubId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Aucun club actif");
        }
        SeanceModele m = new SeanceModele();
        m.setClubId(clubId);
        m.setCreePar(u.getId());
        m.setEquipeOrigineId(u.getEquipeId());
        appliquer(m, req);
        return toResponse(modeleRepository.save(m), true);
    }

    public SeanceModeleResponse modifier(UUID id, SeanceModeleRequest req) {
        Utilisateur u = currentUser.current();
        SeanceModele m = chargeDuClub(id, u);
        exigeCreateur(m, u);
        appliquer(m, req);
        return toResponse(modeleRepository.save(m), true);
    }

    @Transactional
    public void supprimer(UUID id) {
        Utilisateur u = currentUser.current();
        SeanceModele m = chargeDuClub(id, u);
        exigeCreateur(m, u);
        modeleExerciceRepository.deleteBySeanceModeleId(id);
        modeleRepository.deleteById(id);
    }

    /** Remplace l'intégralité des exercices d'un modèle (ordre = ordre de la liste). Créateur-only. */
    @Transactional
    public SeanceModeleDetail remplacerExercices(UUID id, ExercicesRequest req) {
        Utilisateur u = currentUser.current();
        SeanceModele m = chargeDuClub(id, u);
        exigeCreateur(m, u);
        modeleExerciceRepository.deleteBySeanceModeleId(id);
        if (req != null && req.exercices() != null) {
            short ordre = 0;
            for (LigneRequest l : req.exercices()) {
                SeanceModeleExercice se = new SeanceModeleExercice();
                se.setSeanceModeleId(id);
                se.setExerciceId(l.exerciceId());
                se.setOrdre(ordre++);
                se.setDureeMinutes(l.dureeMinutes());
                se.setIntensite(l.intensite());
                se.setDistanceAttendueM(l.distanceAttendueM());
                se.setDistanceHauteIntensiteM(l.distanceHauteIntensiteM());
                se.setNbSprints(l.nbSprints());
                modeleExerciceRepository.save(se);
            }
        }
        return construireDetail(m, true);
    }

    /** Duplique un modèle du club en une copie éditable attribuée à l'utilisateur courant. */
    @Transactional
    public SeanceModeleResponse dupliquer(UUID id) {
        Utilisateur u = currentUser.current();
        SeanceModele source = chargeDuClub(id, u);
        SeanceModele c = new SeanceModele();
        c.setClubId(source.getClubId());
        c.setCreePar(u.getId());
        c.setEquipeOrigineId(u.getEquipeId());
        c.setNom(source.getNom() + " (copie)");
        c.setTypeSeance(source.getTypeSeance());
        c.setObjectif(source.getObjectif());
        c.setDureeMinutes(source.getDureeMinutes());
        c.setObjectifDistanceM(source.getObjectifDistanceM());
        c.setObjectifIntensite(source.getObjectifIntensite());
        c.setObjectifDistanceHauteIntensiteM(source.getObjectifDistanceHauteIntensiteM());
        c.setDescription(source.getDescription());
        SeanceModele copie = modeleRepository.save(c);
        for (SeanceModeleExercice l : modeleExerciceRepository.findBySeanceModeleIdOrderByOrdreAsc(id)) {
            SeanceModeleExercice se = new SeanceModeleExercice();
            se.setSeanceModeleId(copie.getId());
            se.setExerciceId(l.getExerciceId());
            se.setOrdre(l.getOrdre());
            se.setDureeMinutes(l.getDureeMinutes());
            se.setIntensite(l.getIntensite());
            se.setDistanceAttendueM(l.getDistanceAttendueM());
            se.setDistanceHauteIntensiteM(l.getDistanceHauteIntensiteM());
            se.setNbSprints(l.getNbSprints());
            modeleExerciceRepository.save(se);
        }
        return toResponse(copie, true);
    }

    /**
     * Instancie le modèle en une vraie séance PLANIFIÉE : recopie le cadre (type, objectif, durée,
     * objectifs de volume) et les exercices, en la rattachant à l'équipe du contexte d'écriture
     * (même logique que la création manuelle d'une séance).
     */
    @Transactional
    public PlanifieResponse planifier(UUID id, PlanifierRequest req) {
        Utilisateur u = currentUser.current();
        SeanceModele m = chargeDuClub(id, u);

        Seance s = new Seance();
        s.setTypeSeance(m.getTypeSeance());
        s.setDate(req.date());
        s.setTitre(m.getNom());
        s.setStatut("PLANIFIEE");
        s.setHeureDebut(req.heureDebut());
        s.setDureeMinutes(m.getDureeMinutes());
        s.setObjectif(m.getObjectif());
        s.setObjectifDistanceM(m.getObjectifDistanceM());
        s.setObjectifIntensite(m.getObjectifIntensite());
        s.setObjectifDistanceHauteIntensiteM(m.getObjectifDistanceHauteIntensiteM());
        s.setDescription(m.getDescription());
        s.setCreePar(u.getId());
        Seance creee = seanceService.create(s);

        for (SeanceModeleExercice l : modeleExerciceRepository.findBySeanceModeleIdOrderByOrdreAsc(id)) {
            SeanceExercice se = new SeanceExercice();
            se.setSeanceId(creee.getId());
            se.setExerciceId(l.getExerciceId());
            se.setOrdre(l.getOrdre());
            se.setDureeMinutes(l.getDureeMinutes());
            se.setIntensite(l.getIntensite());
            se.setDistanceAttendueM(l.getDistanceAttendueM());
            se.setDistanceHauteIntensiteM(l.getDistanceHauteIntensiteM());
            se.setNbSprints(l.getNbSprints());
            seanceExerciceRepository.save(se);
        }
        return new PlanifieResponse(creee.getId(), creee.getDate());
    }

    // ── Helpers ──

    private UUID clubCourant(Utilisateur u) {
        if (u.getRole() == Role.SUPER_ADMIN) {
            ContexteActif ctx = ContexteActifHolder.get();
            return ctx != null ? ctx.clubId() : null;
        }
        return u.getClubId();
    }

    /** Charge un modèle en vérifiant qu'il relève bien du club courant (anti-IDOR inter-clubs). */
    private SeanceModele chargeDuClub(UUID id, Utilisateur u) {
        SeanceModele m = modeleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Modèle introuvable"));
        UUID clubId = clubCourant(u);
        if (u.getRole() != Role.SUPER_ADMIN && (clubId == null || !clubId.equals(m.getClubId()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Modèle introuvable");
        }
        return m;
    }

    private void exigeCreateur(SeanceModele m, Utilisateur u) {
        if (!estCreateur(m, u)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Seul le créateur peut modifier ce modèle — vous pouvez le dupliquer pour l'adapter");
        }
    }

    private boolean estCreateur(SeanceModele m, Utilisateur u) {
        return u.getRole() == Role.SUPER_ADMIN || (m.getCreePar() != null && m.getCreePar().equals(u.getId()));
    }

    private void appliquer(SeanceModele m, SeanceModeleRequest req) {
        TypeSeance type = typeSeanceRepository.findById(req.typeSeanceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type de séance inconnu"));
        m.setTypeSeance(type);
        m.setNom(req.nom());
        m.setObjectif(req.objectif());
        m.setDureeMinutes(req.dureeMinutes());
        if (req.objectifIntensite() != null && (req.objectifIntensite() < 1 || req.objectifIntensite() > 5)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Intensité attendue entre 1 et 5");
        }
        m.setObjectifIntensite(req.objectifIntensite());
        m.setObjectifDistanceM(req.objectifDistanceM());
        m.setObjectifDistanceHauteIntensiteM(req.objectifDistanceHauteIntensiteM());
        m.setDescription(req.description());
    }

    private SeanceModeleResponse toResponse(SeanceModele m, boolean modifiable) {
        String creeParNom = m.getCreePar() != null
                ? utilisateurRepository.findById(m.getCreePar())
                    .map(c -> ((c.getPrenom() != null ? c.getPrenom() + " " : "") + (c.getNom() != null ? c.getNom() : "")).trim())
                    .orElse(null)
                : null;
        String equipeNom = m.getEquipeOrigineId() != null
                ? equipeRepository.findById(m.getEquipeOrigineId()).map(Equipe::getNom).orElse(null)
                : null;
        int nbExercices = modeleExerciceRepository.findBySeanceModeleIdOrderByOrdreAsc(m.getId()).size();
        TypeSeance type = m.getTypeSeance();
        return new SeanceModeleResponse(
                m.getId(), m.getNom(),
                type != null ? type.getId() : null,
                type != null ? type.getLibelle() : null,
                m.getObjectif(), m.getDureeMinutes(),
                m.getObjectifDistanceM(), m.getObjectifIntensite(), m.getObjectifDistanceHauteIntensiteM(),
                m.getDescription(), nbExercices,
                m.getCreePar(), creeParNom, m.getEquipeOrigineId(), equipeNom, modifiable);
    }

    /** Détail : cadre + lignes d'exercices (valeurs effectives : override sinon défaut) + totaux. */
    private SeanceModeleDetail construireDetail(SeanceModele m, boolean modifiable) {
        List<SeanceModeleExercice> liens = modeleExerciceRepository.findBySeanceModeleIdOrderByOrdreAsc(m.getId());
        Map<UUID, Exercice> exercices = exerciceRepository.findAllById(
                        liens.stream().map(SeanceModeleExercice::getExerciceId).toList())
                .stream().collect(Collectors.toMap(Exercice::getId, Function.identity()));

        List<ExerciceLigne> lignes = new ArrayList<>();
        int dureeTotale = 0;
        double sommePonderee = 0;
        int distanceTotale = 0, distanceHi = 0, sprints = 0;
        boolean aDistance = false, aHi = false, aSprints = false, aIntensite = false;
        int ordre = 0;
        for (SeanceModeleExercice l : liens) {
            Exercice e = exercices.get(l.getExerciceId());
            if (e == null) continue;   // exercice supprimé de la bibliothèque
            Short duree = valeur(l.getDureeMinutes(), e.getDureeMinutes());
            Short intensite = valeur(l.getIntensite(), e.getIntensite());
            Integer distance = valeur(l.getDistanceAttendueM(), e.getDistanceAttendueM());
            Integer distanceHauteIntensite = valeur(l.getDistanceHauteIntensiteM(), e.getDistanceHauteIntensiteM());
            Short nbSprints = valeur(l.getNbSprints(), e.getNbSprints());

            int d = duree != null ? duree : 0;
            dureeTotale += d;
            if (intensite != null) { sommePonderee += (double) intensite * d; aIntensite = true; }
            if (distance != null) { distanceTotale += distance; aDistance = true; }
            if (distanceHauteIntensite != null) { distanceHi += distanceHauteIntensite; aHi = true; }
            if (nbSprints != null) { sprints += nbSprints; aSprints = true; }

            lignes.add(new ExerciceLigne(
                    e.getId(), e.getNom(), e.getCategorie(), e.getType(), ordre++,
                    duree, intensite, e.getObjectif(), e.getDescription(), e.getSchemaJson(),
                    distance, distanceHauteIntensite, nbSprints));
        }
        Double intensiteMoyenne = (aIntensite && dureeTotale > 0)
                ? Math.round((sommePonderee / dureeTotale) * 10) / 10.0 : null;

        return new SeanceModeleDetail(toResponse(m, modifiable), lignes, dureeTotale, intensiteMoyenne,
                aDistance ? distanceTotale : null, aHi ? distanceHi : null, aSprints ? sprints : null);
    }

    private static <T> T valeur(T override, T defaut) {
        return override != null ? override : defaut;
    }
}
