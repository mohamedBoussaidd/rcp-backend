package com.remipreparateur.performance.seance.service;

import com.remipreparateur.performance.gps.entity.DonneeGps;
import com.remipreparateur.performance.seance.dto.SeanceDtos.*;
import com.remipreparateur.performance.seance.entity.Seance;
import com.remipreparateur.performance.seance.entity.SeanceExercice;
import com.remipreparateur.performance.seance.entity.TypeSeance;
import com.remipreparateur.performance.gps.repository.DonneeGpsRepository;
import com.remipreparateur.performance.seance.repository.SeanceExerciceRepository;
import com.remipreparateur.performance.seance.repository.SeanceRepository;
import com.remipreparateur.tactical.exercice.entity.Exercice;
import com.remipreparateur.tactical.exercice.repository.ExerciceRepository;
import com.remipreparateur.shared.security.Scope;
import com.remipreparateur.shared.security.ScopeResolver;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SeanceService {

    private final SeanceRepository seanceRepository;
    private final SeanceExerciceRepository seanceExerciceRepository;
    private final DonneeGpsRepository donneeGpsRepository;
    private final ExerciceRepository exerciceRepository;
    private final ScopeResolver scopeResolver;

    public List<Seance> findAll() {
        Scope s = scopeResolver.resolve();
        if (s.all()) return seanceRepository.findAll();
        if (s.none()) return List.of();
        return seanceRepository.findByEquipeIdIn(s.equipeIds());
    }

    public List<Seance> findByPeriode(LocalDate debut, LocalDate fin) {
        Scope s = scopeResolver.resolve();
        if (s.all()) return seanceRepository.findByDateBetweenOrderByDateAscHeureDebutAsc(debut, fin);
        if (s.none()) return List.of();
        return seanceRepository.findByDateBetweenAndEquipeIdInOrderByDateAscHeureDebutAsc(debut, fin, s.equipeIds());
    }

    public Optional<Seance> findById(UUID id) {
        return seanceRepository.findById(id);
    }

    public Seance save(Seance seance) {
        return seanceRepository.save(seance);
    }

    /** Creation : rattache la seance a l'equipe du staff connecte.
     *  Pour SUPER_ADMIN (equipeId null sur l'utilisateur), on tente l'equipe du contexte actif. */
    public Seance create(Seance seance) {
        UUID equipeId = scopeResolver.equipePourEcriture();
        if (equipeId == null) {
            try { equipeId = scopeResolver.equipeActiveUnique(); }
            catch (ResponseStatusException ignored) {}
        }
        seance.setEquipeId(equipeId);
        return seanceRepository.save(seance);
    }

    public Seance update(UUID id, Seance patch) {
        Seance existing = seanceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Séance introuvable : " + id));
        scopeResolver.verifieAcces(existing.getEquipeId());
        if ("REALISEE".equals(existing.getStatut())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Une seance realisee ne peut plus etre modifiee");
        }
        if (patch.getTitre()             != null) existing.setTitre(patch.getTitre());
        if (patch.getStatut()            != null) existing.setStatut(patch.getStatut());
        if (patch.getDate()              != null) existing.setDate(patch.getDate());
        if (patch.getTypeSeance()        != null) existing.setTypeSeance(patch.getTypeSeance());
        if (patch.getHeureDebut()        != null) existing.setHeureDebut(patch.getHeureDebut());
        if (patch.getDureeMinutes()      != null) existing.setDureeMinutes(patch.getDureeMinutes());
        if (patch.getTerrain()           != null) existing.setTerrain(patch.getTerrain());
        if (patch.getConditionsMeteo()   != null) existing.setConditionsMeteo(patch.getConditionsMeteo());
        if (patch.getAdversaire()        != null) existing.setAdversaire(patch.getAdversaire());
        if (patch.getCompetition()       != null) existing.setCompetition(patch.getCompetition());
        if (patch.getDomicileExterieur() != null) existing.setDomicileExterieur(patch.getDomicileExterieur());
        if (patch.getScoreMatch()        != null) existing.setScoreMatch(patch.getScoreMatch());
        if (patch.getDescription()       != null) existing.setDescription(patch.getDescription());
        if (patch.getResponsable()       != null) existing.setResponsable(patch.getResponsable());
        existing.setTypeSeance((TypeSeance) Hibernate.unproxy(existing.getTypeSeance()));
        return seanceRepository.save(existing);
    }

    @Transactional
    public void delete(UUID id) {
        seanceRepository.findById(id).ifPresent(s -> {
            scopeResolver.verifieAcces(s.getEquipeId());
            donneeGpsRepository.deleteBySeanceId(id);
            seanceRepository.deleteById(id);
        });
    }

    public Seance marquerRealisee(UUID id) {
        Seance s = seanceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Séance introuvable : " + id));
        scopeResolver.verifieAcces(s.getEquipeId());
        s.setStatut("REALISEE");
        s.setTypeSeance((TypeSeance) Hibernate.unproxy(s.getTypeSeance()));
        return seanceRepository.save(s);
    }

    public List<DonneeGps> findDonneesGpsBySeance(UUID seanceId) {
        return donneeGpsRepository.findBySeanceId(seanceId);
    }

    // ══════════ Exercices d'une séance (référence + overrides) ══════════

    /** Contenu d'une séance : lignes d'exercices (valeurs effectives) + agrégats. */
    public ContenuSeance getContenu(UUID seanceId) {
        Seance seance = seanceRepository.findById(seanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Séance introuvable"));
        scopeResolver.verifieAcces(seance.getEquipeId());

        List<SeanceExercice> liens = seanceExerciceRepository.findBySeanceIdOrderByOrdreAsc(seanceId);
        Map<UUID, Exercice> exercices = exerciceRepository.findAllById(
                        liens.stream().map(SeanceExercice::getExerciceId).toList())
                .stream().collect(Collectors.toMap(Exercice::getId, Function.identity()));

        List<ExerciceLigne> lignes = new ArrayList<>();
        int dureeTotale = 0;
        double sommePonderee = 0;
        int distanceTotale = 0, distanceHi = 0, sprints = 0;
        boolean aDistance = false, aHi = false, aSprints = false, aIntensite = false;
        int ordre = 0;
        for (SeanceExercice l : liens) {
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

        return new ContenuSeance(seanceId, lignes, dureeTotale, intensiteMoyenne,
                aDistance ? distanceTotale : null,
                aHi ? distanceHi : null,
                aSprints ? sprints : null);
    }

    /** Remplace l'intégralité des exercices d'une séance (ordre = ordre de la liste). */
    @Transactional
    public ContenuSeance remplacerExercices(UUID seanceId, ExercicesRequest req) {
        Seance seance = seanceRepository.findById(seanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Séance introuvable"));
        scopeResolver.verifieAcces(seance.getEquipeId());
        if ("REALISEE".equals(seance.getStatut())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Une seance realisee ne peut plus etre modifiee");
        }
        seanceExerciceRepository.deleteBySeanceId(seanceId);
        if (req != null && req.exercices() != null) {
            short ordre = 0;
            for (LigneRequest l : req.exercices()) {
                SeanceExercice se = new SeanceExercice();
                se.setSeanceId(seanceId);
                se.setExerciceId(l.exerciceId());
                se.setOrdre(ordre++);
                se.setDureeMinutes(l.dureeMinutes());
                se.setIntensite(l.intensite());
                se.setDistanceAttendueM(l.distanceAttendueM());
                se.setDistanceHauteIntensiteM(l.distanceHauteIntensiteM());
                se.setNbSprints(l.nbSprints());
                seanceExerciceRepository.save(se);
            }
        }
        return getContenu(seanceId);
    }

    private static <T> T valeur(T override, T defaut) {
        return override != null ? override : defaut;
    }
}
