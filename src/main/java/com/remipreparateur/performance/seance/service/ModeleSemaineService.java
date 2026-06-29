package com.remipreparateur.performance.seance.service;

import com.remipreparateur.performance.seance.dto.ModeleSemaineDtos.*;
import com.remipreparateur.performance.seance.entity.CreneauModele;
import com.remipreparateur.performance.seance.entity.ModeleSemaine;
import com.remipreparateur.performance.seance.entity.Seance;
import com.remipreparateur.performance.seance.entity.TypeSeance;
import com.remipreparateur.performance.seance.repository.CreneauModeleRepository;
import com.remipreparateur.performance.seance.repository.ModeleSemaineRepository;
import com.remipreparateur.performance.seance.repository.SeanceRepository;
import com.remipreparateur.performance.seance.repository.TypeSeanceRepository;
import com.remipreparateur.shared.security.Scope;
import com.remipreparateur.shared.security.ScopeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ModeleSemaineService {

    private final ModeleSemaineRepository modeleRepository;
    private final CreneauModeleRepository creneauRepository;
    private final SeanceRepository seanceRepository;
    private final TypeSeanceRepository typeSeanceRepository;
    private final ScopeResolver scopeResolver;

    // ══════════ Lecture ══════════

    public List<ModeleDto> findAll() {
        Scope s = scopeResolver.resolve();
        List<ModeleSemaine> modeles;
        if (s.all())       modeles = modeleRepository.findAll();
        else if (s.none()) return List.of();
        else               modeles = modeleRepository.findByEquipeIdInOrderByNomAsc(s.equipeIds());
        return modeles.stream().map(this::toDto).collect(Collectors.toList());
    }

    public ModeleDto get(UUID id) {
        ModeleSemaine m = charge(id);
        return toDto(m);
    }

    // ══════════ Écriture ══════════

    @Transactional
    public ModeleDto create(ModeleRequest req) {
        UUID equipeId = scopeResolver.equipePourEcriture();
        if (equipeId == null) {
            try { equipeId = scopeResolver.equipeActiveUnique(); }
            catch (ResponseStatusException ignored) {}
        }
        if (equipeId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucune équipe cible pour le modèle");
        }
        ModeleSemaine m = new ModeleSemaine();
        m.setEquipeId(equipeId);
        appliquer(m, req);
        m = modeleRepository.save(m);
        remplacerCreneaux(m.getId(), req);
        return toDto(charge(m.getId()));
    }

    @Transactional
    public ModeleDto update(UUID id, ModeleRequest req) {
        ModeleSemaine m = charge(id);
        appliquer(m, req);
        m.setUpdatedAt(LocalDateTime.now());
        modeleRepository.save(m);
        if (req.creneaux() != null) remplacerCreneaux(id, req);
        return toDto(charge(id));
    }

    @Transactional
    public ModeleDto dupliquer(UUID id) {
        ModeleSemaine src = charge(id);
        ModeleSemaine copie = new ModeleSemaine();
        copie.setEquipeId(src.getEquipeId());
        copie.setNom(src.getNom() + " (copie)");
        copie.setDescription(src.getDescription());
        copie = modeleRepository.save(copie);
        for (CreneauModele c : creneauRepository
                .findByModeleIdOrderByJourSemaineAscHeureDebutAscOrdreAsc(id)) {
            CreneauModele n = new CreneauModele();
            n.setModeleId(copie.getId());
            n.setJourSemaine(c.getJourSemaine());
            n.setHeureDebut(c.getHeureDebut());
            n.setDureeMinutes(c.getDureeMinutes());
            n.setTerrain(c.getTerrain());
            n.setTypeSeance(c.getTypeSeance());
            n.setTitre(c.getTitre());
            n.setObjectif(c.getObjectif());
            n.setObjectifDistanceM(c.getObjectifDistanceM());
            n.setObjectifIntensite(c.getObjectifIntensite());
            n.setOrdre(c.getOrdre());
            creneauRepository.save(n);
        }
        return toDto(charge(copie.getId()));
    }

    @Transactional
    public void delete(UUID id) {
        ModeleSemaine m = charge(id);
        creneauRepository.deleteByModeleId(m.getId());
        modeleRepository.deleteById(m.getId());
    }

    // ══════════ Instanciation ══════════

    /**
     * Génère les vraies séances du modèle pour chaque date de [debut ; fin] (incluses)
     * dont le jour correspond à un créneau. Les séances créées sont indépendantes du
     * modèle (snapshot). Une séance déjà REALISEE n'est jamais touchée.
     */
    @Transactional
    public InstancierResult instancier(UUID id, InstancierRequest req) {
        ModeleSemaine m = charge(id);
        if (req.debut() == null || req.fin() == null || req.fin().isBefore(req.debut())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plage de dates invalide");
        }
        List<CreneauModele> creneaux = creneauRepository
                .findByModeleIdOrderByJourSemaineAscHeureDebutAscOrdreAsc(id);

        // Séances déjà posées sur la plage pour l'équipe, indexées par (date|typeSeanceId).
        Map<String, List<Seance>> existantes = seanceRepository
                .findByDateBetweenAndEquipeIdInOrderByDateAscHeureDebutAsc(
                        req.debut(), req.fin(), List.of(m.getEquipeId()))
                .stream()
                .collect(Collectors.groupingBy(s -> cle(s.getDate(), s.getTypeSeance().getId())));

        int creees = 0, ignorees = 0, remplacees = 0;
        for (LocalDate d = req.debut(); !d.isAfter(req.fin()); d = d.plusDays(1)) {
            short dow = (short) d.getDayOfWeek().getValue();   // 1 = lundi … 7 = dimanche
            for (CreneauModele c : creneaux) {
                if (c.getJourSemaine() == null || c.getJourSemaine() != dow) continue;
                String k = cle(d, c.getTypeSeance().getId());
                List<Seance> deja = existantes.get(k);
                if (deja != null && !deja.isEmpty()) {
                    boolean realisee = deja.stream().anyMatch(s -> "REALISEE".equals(s.getStatut()));
                    if (realisee) { ignorees++; continue; }
                    if (!req.remplacer()) { ignorees++; continue; }
                    for (Seance s : deja) seanceRepository.deleteById(s.getId());
                    remplacees++;
                }
                seanceRepository.save(versSeance(m, c, d));
                if (deja == null || deja.isEmpty()) creees++;
            }
        }
        return new InstancierResult(creees, ignorees, remplacees);
    }

    private Seance versSeance(ModeleSemaine m, CreneauModele c, LocalDate date) {
        Seance s = new Seance();
        s.setEquipeId(m.getEquipeId());
        s.setTypeSeance(c.getTypeSeance());
        s.setDate(date);
        s.setStatut("PLANIFIEE");
        s.setHeureDebut(c.getHeureDebut());
        s.setDureeMinutes(c.getDureeMinutes());
        s.setTerrain(terrainValide(c.getTerrain()));
        s.setTitre(c.getTitre());
        s.setObjectif(c.getObjectif());
        s.setObjectifDistanceM(c.getObjectifDistanceM());
        s.setObjectifIntensite(c.getObjectifIntensite());
        return s;
    }

    // ══════════ Helpers ══════════

    /** Valeurs autorisées par la contrainte seance_terrain_check (cf. V1). */
    private static final java.util.Set<String> TERRAINS_VALIDES = java.util.Set.of(
            "SYNTHETIQUE", "HERBE", "HERBE_GRASSE", "PARQUET", "GOUDRON", "FORET");

    /** Le terrain du créneau (texte libre historique) n'est conservé que s'il respecte la contrainte. */
    private static String terrainValide(String terrain) {
        return terrain != null && TERRAINS_VALIDES.contains(terrain) ? terrain : null;
    }

    private static String cle(LocalDate date, UUID typeSeanceId) {
        return date + "|" + typeSeanceId;
    }

    private ModeleSemaine charge(UUID id) {
        ModeleSemaine m = modeleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Modèle introuvable"));
        scopeResolver.verifieAcces(m.getEquipeId());
        return m;
    }

    private void appliquer(ModeleSemaine m, ModeleRequest req) {
        if (req.nom() == null || req.nom().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le nom du modèle est obligatoire");
        }
        m.setNom(req.nom().trim());
        m.setDescription(req.description());
    }

    private void remplacerCreneaux(UUID modeleId, ModeleRequest req) {
        creneauRepository.deleteByModeleId(modeleId);
        if (req.creneaux() == null) return;
        short ordre = 0;
        for (CreneauDto dto : req.creneaux()) {
            TypeSeance type = typeSeanceRepository.findById(dto.typeSeanceId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Type de séance introuvable : " + dto.typeSeanceId()));
            CreneauModele c = new CreneauModele();
            c.setModeleId(modeleId);
            c.setJourSemaine(dto.jourSemaine());
            c.setHeureDebut(dto.heureDebut());
            c.setDureeMinutes(dto.dureeMinutes());
            c.setTerrain(dto.terrain());
            c.setTypeSeance(type);
            c.setTitre(dto.titre());
            c.setObjectif(dto.objectif());
            c.setObjectifDistanceM(dto.objectifDistanceM());
            c.setObjectifIntensite(dto.objectifIntensite());
            c.setOrdre(dto.ordre() != 0 ? dto.ordre() : ordre);
            creneauRepository.save(c);
            ordre++;
        }
    }

    private ModeleDto toDto(ModeleSemaine m) {
        List<CreneauDto> creneaux = new ArrayList<>();
        for (CreneauModele c : creneauRepository
                .findByModeleIdOrderByJourSemaineAscHeureDebutAscOrdreAsc(m.getId())) {
            TypeSeance t = c.getTypeSeance();
            creneaux.add(new CreneauDto(
                    c.getId(),
                    c.getJourSemaine() != null ? c.getJourSemaine() : 1,
                    c.getHeureDebut(),
                    c.getDureeMinutes(),
                    c.getTerrain(),
                    t != null ? t.getId() : null,
                    t != null ? t.getLibelle() : null,
                    c.getTitre(),
                    c.getObjectif(),
                    c.getObjectifDistanceM(),
                    c.getObjectifIntensite(),
                    c.getOrdre() != null ? c.getOrdre() : 0));
        }
        return new ModeleDto(m.getId(), m.getEquipeId(), m.getNom(), m.getDescription(), creneaux);
    }
}
