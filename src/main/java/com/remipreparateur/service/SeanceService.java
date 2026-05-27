package com.remipreparateur.service;

import com.remipreparateur.entity.DonneeGps;
import com.remipreparateur.entity.Seance;
import com.remipreparateur.repository.DonneeGpsRepository;
import com.remipreparateur.repository.SeanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SeanceService {

    private final SeanceRepository seanceRepository;
    private final DonneeGpsRepository donneeGpsRepository;

    public List<Seance> findAll() {
        return seanceRepository.findAll();
    }

    public List<Seance> findByPeriode(LocalDate debut, LocalDate fin) {
        return seanceRepository.findByDateBetweenOrderByDateAscHeureDebutAsc(debut, fin);
    }

    public Optional<Seance> findById(UUID id) {
        return seanceRepository.findById(id);
    }

    public Seance save(Seance seance) {
        return seanceRepository.save(seance);
    }

    public Seance update(UUID id, Seance patch) {
        Seance existing = seanceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Séance introuvable : " + id));
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
        return seanceRepository.save(existing);
    }

    @Transactional
    public void delete(UUID id) {
        donneeGpsRepository.deleteBySeanceId(id);
        seanceRepository.deleteById(id);
    }

    public Seance marquerRealisee(UUID id) {
        Seance s = seanceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Séance introuvable : " + id));
        s.setStatut("REALISEE");
        return seanceRepository.save(s);
    }

    public List<DonneeGps> findDonneesGpsBySeance(UUID seanceId) {
        return donneeGpsRepository.findBySeanceId(seanceId);
    }
}
