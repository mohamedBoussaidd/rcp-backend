package com.remipreparateur.service;

import com.remipreparateur.dto.GpsHistoriqueDto;
import com.remipreparateur.entity.Joueur;
import com.remipreparateur.repository.DonneeGpsRepository;
import com.remipreparateur.repository.JoueurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JoueurService {

    private final JoueurRepository joueurRepository;
    private final DonneeGpsRepository donneeGpsRepository;

    public List<Joueur> findAll() {
        return joueurRepository.findByStatut("actif");
    }

    public Optional<Joueur> findById(UUID id) {
        return joueurRepository.findById(id);
    }

    public Joueur save(Joueur joueur) {
        return joueurRepository.save(joueur);
    }

    public List<Joueur> findAllPlayers() {
        return joueurRepository.findAll();
    }

    public void deleteById(UUID id) {
        joueurRepository.deleteById(id);
    }

    public List<GpsHistoriqueDto> getHistoriqueGps(UUID joueurId) {
        return donneeGpsRepository.findByJoueurIdOrderBySeanceDateDesc(joueurId)
                .stream()
                .map(d -> new GpsHistoriqueDto(
                        d.getSeance().getId(),
                        d.getSeance().getDate(),
                        d.getSeance().getTypeSeance().getCode(),
                        d.getSeance().getTypeSeance().getLibelle(),
                        d.getDureeMinutes(),
                        d.getDistanceTotaleM(),
                        d.getDistance15kmhM(),
                        d.getDistance19kmhM(),
                        d.getDistanceSprint24kmhM(),
                        d.getDistanceSprint28kmhM(),
                        d.getNbSprints24kmh(),
                        d.getVitesseMaxKmh(),
                        d.getNbAccelerations(),
                        d.getNbFreinages(),
                        d.getRatioDistanceMin()
                ))
                .toList();
    }
}
