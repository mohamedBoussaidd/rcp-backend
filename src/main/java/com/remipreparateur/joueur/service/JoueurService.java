package com.remipreparateur.joueur.service;

import com.remipreparateur.performance.gps.dto.GpsHistoriqueDto;
import com.remipreparateur.performance.gps.dto.VitesseJoueurDto;
import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.performance.gps.repository.DonneeGpsRepository;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.shared.security.Scope;
import com.remipreparateur.shared.security.ScopeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JoueurService {

    private final JoueurRepository joueurRepository;
    private final DonneeGpsRepository donneeGpsRepository;
    private final ScopeResolver scopeResolver;

    /** Liste des joueurs actifs, limitee a la portee de l'utilisateur (equipe). */
    public List<Joueur> findAll() {
        Scope s = scopeResolver.resolve();
        if (s.all()) return joueurRepository.findByStatutNot("inactif");
        if (s.none()) return List.of();
        return joueurRepository.findByStatutNotAndEquipeIdIn("inactif", s.equipeIds());
    }

    public Optional<Joueur> findById(UUID id) {
        return joueurRepository.findById(id);
    }

    public Joueur save(Joueur joueur) {
        return joueurRepository.save(joueur);
    }

    /** Creation : rattache le joueur a l'equipe du staff connecte. */
    public Joueur create(Joueur joueur) {
        joueur.setEquipeId(scopeResolver.equipePourEcriture());
        return joueurRepository.save(joueur);
    }

    /** Tous les joueurs (y compris inactifs), limites a la portee de l'utilisateur. */
    public List<Joueur> findAllPlayers() {
        Scope s = scopeResolver.resolve();
        if (s.all()) return joueurRepository.findAll();
        if (s.none()) return List.of();
        return joueurRepository.findByEquipeIdIn(s.equipeIds());
    }

    public void deleteById(UUID id) {
        joueurRepository.deleteById(id);
    }

    /** Fiche vitesse (vmax record, vmoy = moyenne des vmax) par joueur, limitée à la portée. */
    public List<VitesseJoueurDto> getVitesses() {
        Scope s = scopeResolver.resolve();
        if (s.none()) return List.of();
        List<DonneeGpsRepository.VitesseAgg> aggs = s.all()
                ? donneeGpsRepository.aggregerToutesVitesses()
                : donneeGpsRepository.aggregerVitesses(s.equipeIds());
        return aggs.stream()
                .map(a -> new VitesseJoueurDto(
                        a.getJoueurId(),
                        a.getVmax(),
                        a.getVmoy() == null ? null
                                : BigDecimal.valueOf(a.getVmoy()).setScale(1, RoundingMode.HALF_UP)))
                .toList();
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
                        d.getRatioDistanceMin(),
                        d.getSeance().getConditionsMeteo(),
                        d.getSeance().getTemperature()
                ))
                .toList();
    }
}
