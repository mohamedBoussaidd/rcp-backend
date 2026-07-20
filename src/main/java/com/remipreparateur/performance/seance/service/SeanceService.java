package com.remipreparateur.performance.seance.service;

import com.remipreparateur.auth.repository.UtilisateurRepository;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.performance.gps.entity.DonneeGps;
import com.remipreparateur.performance.seance.dto.SeanceDtos.*;
import com.remipreparateur.performance.seance.entity.BlocSeance;
import com.remipreparateur.performance.seance.entity.GroupeSeance;
import com.remipreparateur.performance.seance.entity.Seance;
import com.remipreparateur.performance.seance.entity.SeanceDominante;
import com.remipreparateur.performance.seance.entity.SeanceExercice;
import com.remipreparateur.performance.seance.entity.SeanceSousPrincipe;
import com.remipreparateur.performance.seance.entity.TypeSeance;
import com.remipreparateur.performance.gps.repository.DonneeGpsRepository;
import com.remipreparateur.performance.seance.repository.BlocSeanceRepository;
import com.remipreparateur.performance.seance.repository.GroupeSeanceRepository;
import com.remipreparateur.performance.seance.repository.SeanceDominanteRepository;
import com.remipreparateur.performance.seance.repository.SeanceExerciceRepository;
import com.remipreparateur.performance.seance.repository.SeanceRepository;
import com.remipreparateur.performance.seance.repository.SeanceSousPrincipeRepository;
import com.remipreparateur.tactical.exercice.entity.Exercice;
import com.remipreparateur.tactical.exercice.repository.ExerciceRepository;
import com.remipreparateur.shared.security.Authorites;
import com.remipreparateur.shared.security.Scope;
import com.remipreparateur.shared.security.ScopeResolver;
import com.remipreparateur.shared.time.Horloge;
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
    private final BlocSeanceRepository blocSeanceRepository;
    private final GroupeSeanceRepository groupeSeanceRepository;
    private final SeanceDominanteRepository seanceDominanteRepository;
    private final SeanceSousPrincipeRepository seanceSousPrincipeRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final com.remipreparateur.club.repository.EquipeRepository equipeRepository;
    private final JoueurRepository joueurRepository;
    private final ScopeResolver scopeResolver;
    private final Horloge horloge;

    public List<Seance> findAll() {
        Scope s = scopeResolver.resolve();
        if (s.all()) return seanceRepository.findAll();
        if (s.none()) return List.of();
        return seanceRepository.findByEquipeIdIn(s.equipeIds());
    }

    public List<Seance> findByPeriode(LocalDate debut, LocalDate fin) {
        // Hybride « voyage dans la saison » : en date simulée (super-admin), on masque le futur en
        // capant la borne haute à la date simulée. Hors simulation, comportement inchangé — le
        // calendrier continue d'afficher les séances futures planifiées.
        if (horloge.estSimulee() && fin != null && fin.isAfter(horloge.today())) fin = horloge.today();
        if (debut != null && fin != null && fin.isBefore(debut)) return List.of();
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
        // Champs du mode avancé : ignorés sans seance_avancee:access (module + rôle).
        if (!accesAvance()) {
            seance.setDureeEffectiveMinutes(null);
            seance.setObjTactiqueOrg(null);
            seance.setObjTactiqueFonc(null);
            seance.setObjMental(null);
            seance.setObjTechnique(null);
            seance.setObjAthletique(null);
        }
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
        if (accesAvance()) {
            if (patch.getDureeEffectiveMinutes() != null) existing.setDureeEffectiveMinutes(patch.getDureeEffectiveMinutes());
            if (patch.getObjTactiqueOrg()  != null) existing.setObjTactiqueOrg(patch.getObjTactiqueOrg());
            if (patch.getObjTactiqueFonc() != null) existing.setObjTactiqueFonc(patch.getObjTactiqueFonc());
            if (patch.getObjMental()       != null) existing.setObjMental(patch.getObjMental());
            if (patch.getObjTechnique()    != null) existing.setObjTechnique(patch.getObjTechnique());
            if (patch.getObjAthletique()   != null) existing.setObjAthletique(patch.getObjAthletique());
        }
        existing.setTypeSeance((TypeSeance) Hibernate.unproxy(existing.getTypeSeance()));
        return seanceRepository.save(existing);
    }

    /** Droit d'éditer les enrichissements du mode avancé (module seance_avancee actif + rôle). */
    private boolean accesAvance() {
        return Authorites.possede("seance_avancee:access");
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
        // Une séance n'est "réalisée" qu'à partir du jour même (jour J ou passé) ; jamais une séance future.
        if (s.getDate() != null && s.getDate().isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Impossible de marquer réalisée une séance future");
        }
        s.setStatut("REALISEE");
        s.setTypeSeance((TypeSeance) Hibernate.unproxy(s.getTypeSeance()));
        return seanceRepository.save(s);
    }

    /** Retour arrière : repasse une séance réalisée en PLANIFIEE. Bloqué si des données GPS sont attachées. */
    public Seance annulerRealisation(UUID id) {
        Seance s = seanceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Séance introuvable : " + id));
        scopeResolver.verifieAcces(s.getEquipeId());
        if (!"REALISEE".equals(s.getStatut())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Seule une séance réalisée peut être dé-réalisée");
        }
        if (donneeGpsRepository.existsBySeanceId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Supprime d'abord les données GPS de cette séance");
        }
        s.setStatut("PLANIFIEE");
        s.setTypeSeance((TypeSeance) Hibernate.unproxy(s.getTypeSeance()));
        return seanceRepository.save(s);
    }

    public List<DonneeGps> findDonneesGpsBySeance(UUID seanceId) {
        return donneeGpsRepository.findBySeanceId(seanceId);
    }

    // ══════════ Exercices d'une séance (référence + overrides) ══════════

    /** Contenu d'une séance : lignes d'exercices (valeurs effectives) + agrégats +
     *  (mode avancé) blocs, groupes et sélections de référentiels. */
    public ContenuSeance getContenu(UUID seanceId) {
        Seance seance = seanceRepository.findById(seanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Séance introuvable"));
        scopeResolver.verifieAcces(seance.getEquipeId());
        return contenuInterne(seanceId);
    }

    /** Variante SANS contrôle de scope staff — réservée aux chemins déjà vérifiés autrement
     *  (fiche joueur : l'appartenance du joueur à l'équipe de la séance est contrôlée en amont). */
    public ContenuSeance getContenuSansScope(UUID seanceId) {
        return contenuInterne(seanceId);
    }

    private ContenuSeance contenuInterne(UUID seanceId) {
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
                    distance, distanceHauteIntensite, nbSprints, l.getBlocId()));
        }
        Double intensiteMoyenne = (aIntensite && dureeTotale > 0)
                ? Math.round((sommePonderee / dureeTotale) * 10) / 10.0 : null;

        return new ContenuSeance(seanceId, lignes, dureeTotale, intensiteMoyenne,
                aDistance ? distanceTotale : null,
                aHi ? distanceHi : null,
                aSprints ? sprints : null,
                blocsDe(seanceId), groupesDe(seanceId),
                seanceDominanteRepository.findBySeanceId(seanceId).stream()
                        .map(SeanceDominante::getDominanteId).toList(),
                seanceSousPrincipeRepository.findBySeanceId(seanceId).stream()
                        .map(SeanceSousPrincipe::getSousPrincipeId).toList());
    }

    /** Blocs d'une séance avec le staff résolu (nom + rôle + équipe, pour départager les homonymes). */
    public List<BlocDto> blocsDe(UUID seanceId) {
        Map<UUID, String> equipes = new java.util.HashMap<>();
        return blocSeanceRepository.findBySeanceIdOrderByOrdreAsc(seanceId).stream()
                .map(b -> new BlocDto(b.getId(), b.getOrdre(), b.getLibelle(), b.getSequencage(),
                        b.getDureeMinutes(), b.getZoneTerrain(),
                        b.getStaffIds().stream()
                                .map(id -> utilisateurRepository.findById(id)
                                        .map(u -> StaffRef.de(u.getId(), u.getPrenom(), u.getNom(), u.getRole(),
                                                u.getEquipeId() == null ? null
                                                        : equipes.computeIfAbsent(u.getEquipeId(),
                                                                e -> equipeRepository.findById(e)
                                                                        .map(com.remipreparateur.club.entity.Equipe::getNom)
                                                                        .orElse(null))))
                                        .orElse(null))
                                .filter(s -> s != null)
                                .toList()))
                .toList();
    }

    /** Groupes du jour stockés (COULEUR / LIBRE) avec les joueurs résolus. */
    public List<GroupeDto> groupesDe(UUID seanceId) {
        return groupeSeanceRepository.findBySeanceIdOrderByOrdreAsc(seanceId).stream()
                .map(g -> new GroupeDto(g.getId(), g.getBlocId(), g.getType(), g.getLibelle(),
                        g.getCouleur(), g.getOrdre(),
                        joueurRepository.findAllById(g.getJoueurIds()).stream()
                                .map(j -> new JoueurRef(j.getId(), j.getNom(), j.getPrenom()))
                                .toList()))
                .toList();
    }

    /** Remplace l'intégralité des exercices d'une séance (ordre = ordre de la liste).
     *  Endpoint historique « liste plate » : les lignes recréées sont hors bloc. */
    @Transactional
    public ContenuSeance remplacerExercices(UUID seanceId, ExercicesRequest req) {
        Seance seance = chargePourEdition(seanceId);
        seanceExerciceRepository.deleteBySeanceId(seanceId);
        if (req != null && req.exercices() != null) {
            creerLignes(seanceId, req.exercices(), List.of());
        }
        return getContenu(seance.getId());
    }

    /**
     * Remplacement complet du contenu AVANCÉ : blocs + lignes (rattachées par blocIndex) +
     * groupes du jour + dominantes + sous-principes. Réservé au module seance_avancee.
     */
    @Transactional
    public ContenuSeance remplacerContenuAvance(UUID seanceId, ContenuAvanceRequest req) {
        if (!accesAvance()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Mode avancé non disponible (module seance_avancee)");
        }
        Seance seance = chargePourEdition(seanceId);

        // Purge dans l'ordre des dépendances (groupes et lignes référencent les blocs).
        groupeSeanceRepository.deleteBySeanceId(seanceId);
        seanceExerciceRepository.deleteBySeanceId(seanceId);
        blocSeanceRepository.deleteBySeanceId(seanceId);
        seanceDominanteRepository.deleteBySeanceId(seanceId);
        seanceSousPrincipeRepository.deleteBySeanceId(seanceId);

        List<UUID> blocIds = new ArrayList<>();
        if (req.blocs() != null) {
            short ordre = 0;
            for (BlocRequest b : req.blocs()) {
                BlocSeance bloc = new BlocSeance();
                bloc.setSeanceId(seanceId);
                bloc.setOrdre(ordre++);
                bloc.setLibelle(b.libelle() == null || b.libelle().isBlank() ? "Bloc " + ordre : b.libelle());
                bloc.setSequencage(b.sequencage());
                bloc.setDureeMinutes(b.dureeMinutes());
                bloc.setZoneTerrain(b.zoneTerrain());
                if (b.staffIds() != null) bloc.setStaffIds(new ArrayList<>(b.staffIds()));
                blocIds.add(blocSeanceRepository.save(bloc).getId());
            }
        }
        if (req.exercices() != null) {
            creerLignes(seanceId, req.exercices(), blocIds);
        }
        if (req.groupes() != null) {
            short ordre = 0;
            for (GroupeRequest g : req.groupes()) {
                GroupeSeance groupe = new GroupeSeance();
                groupe.setSeanceId(seanceId);
                groupe.setBlocId(blocIdPourIndex(g.blocIndex(), blocIds));
                groupe.setType("COULEUR".equals(g.type()) ? "COULEUR" : "LIBRE");
                groupe.setLibelle(g.libelle() == null || g.libelle().isBlank() ? "Groupe" : g.libelle());
                groupe.setCouleur(g.couleur());
                groupe.setOrdre(ordre++);
                if (g.joueurIds() != null) groupe.setJoueurIds(new ArrayList<>(g.joueurIds()));
                groupeSeanceRepository.save(groupe);
            }
        }
        if (req.dominanteIds() != null) {
            for (UUID id : req.dominanteIds()) {
                SeanceDominante sd = new SeanceDominante();
                sd.setSeanceId(seanceId);
                sd.setDominanteId(id);
                seanceDominanteRepository.save(sd);
            }
        }
        if (req.sousPrincipeIds() != null) {
            for (UUID id : req.sousPrincipeIds()) {
                SeanceSousPrincipe sp = new SeanceSousPrincipe();
                sp.setSeanceId(seanceId);
                sp.setSousPrincipeId(id);
                seanceSousPrincipeRepository.save(sp);
            }
        }
        return getContenu(seance.getId());
    }

    private Seance chargePourEdition(UUID seanceId) {
        Seance seance = seanceRepository.findById(seanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Séance introuvable"));
        scopeResolver.verifieAcces(seance.getEquipeId());
        if ("REALISEE".equals(seance.getStatut())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Une seance realisee ne peut plus etre modifiee");
        }
        return seance;
    }

    private void creerLignes(UUID seanceId, List<LigneRequest> lignes, List<UUID> blocIds) {
        short ordre = 0;
        for (LigneRequest l : lignes) {
            SeanceExercice se = new SeanceExercice();
            se.setSeanceId(seanceId);
            se.setExerciceId(l.exerciceId());
            se.setOrdre(ordre++);
            se.setBlocId(blocIdPourIndex(l.blocIndex(), blocIds));
            se.setDureeMinutes(l.dureeMinutes());
            se.setIntensite(l.intensite());
            se.setDistanceAttendueM(l.distanceAttendueM());
            se.setDistanceHauteIntensiteM(l.distanceHauteIntensiteM());
            se.setNbSprints(l.nbSprints());
            seanceExerciceRepository.save(se);
        }
    }

    private UUID blocIdPourIndex(Integer index, List<UUID> blocIds) {
        return (index != null && index >= 0 && index < blocIds.size()) ? blocIds.get(index) : null;
    }

    private static <T> T valeur(T override, T defaut) {
        return override != null ? override : defaut;
    }
}
