package com.remipreparateur.performance.seance.service;

import com.remipreparateur.performance.gps.entity.DonneeGps;
import com.remipreparateur.performance.seance.entity.Seance;
import com.remipreparateur.performance.seance.entity.TypeSeance;
import com.remipreparateur.performance.gps.repository.DonneeGpsRepository;
import com.remipreparateur.performance.seance.repository.SeanceRepository;
import com.remipreparateur.shared.security.Scope;
import com.remipreparateur.shared.security.ScopeResolver;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SeanceService {

    private final SeanceRepository seanceRepository;
    private final DonneeGpsRepository donneeGpsRepository;
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

    /** Creation : rattache la seance a l'equipe du staff connecte. */
    public Seance create(Seance seance) {
        seance.setEquipeId(scopeResolver.equipePourEcriture());
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
}
