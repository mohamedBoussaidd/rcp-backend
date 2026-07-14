package com.remipreparateur.joueur.service;

import com.remipreparateur.performance.gps.dto.GpsHistoriqueDto;
import com.remipreparateur.performance.gps.dto.VitesseJoueurDto;
import com.remipreparateur.club.entity.Equipe;
import com.remipreparateur.club.repository.EquipeRepository;
import com.remipreparateur.joueur.dto.AnnuaireJoueurDto;
import com.remipreparateur.joueur.dto.AnnuaireJoueurDto.EquipeRef;
import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.performance.gps.repository.DonneeGpsRepository;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.saison.entity.EffectifSaison;
import com.remipreparateur.saison.entity.Saison;
import com.remipreparateur.saison.repository.EffectifSaisonRepository;
import com.remipreparateur.saison.repository.SaisonRepository;
import com.remipreparateur.shared.security.Scope;
import com.remipreparateur.shared.security.ScopeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JoueurService {

    private final JoueurRepository joueurRepository;
    private final DonneeGpsRepository donneeGpsRepository;
    private final ScopeResolver scopeResolver;
    private final SaisonRepository saisonRepository;
    private final EffectifSaisonRepository effectifRepository;
    private final EquipeRepository equipeRepository;

    /**
     * Liste des fiches actives visibles. Rôles « club entier » (président/administratif/super-admin
     * en contexte) : TOUTES les fiches du club, y compris celles non assignées à une équipe (pool).
     * Staff scopé équipe : uniquement les fiches de ses équipes (comportement historique).
     */
    public List<Joueur> findAll() {
        UUID club = scopeResolver.clubEntierPourGestion();
        if (club != null) return joueurRepository.findJoueursActifsByClub(club);
        Scope s = scopeResolver.resolve();
        // Super-admin SANS contexte club : on ne liste jamais toute la plateforme (une page
        // d'effectif afficherait les fiches de tous les clubs mélangées) → il doit choisir un club.
        if (s.all()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Sélectionnez un club");
        if (s.none()) return List.of();
        return joueurRepository.findByStatutNotAndEquipeIdIn("inactif", s.equipeIds());
    }

    public Optional<Joueur> findById(UUID id) {
        return joueurRepository.findById(id);
    }

    public Joueur save(Joueur joueur) {
        return joueurRepository.save(joueur);
    }

    /**
     * Création d'une fiche : rattachée au CLUB (niveau club). L'équipe est optionnelle — soit
     * fournie explicitement (scope vérifié), soit l'équipe du staff créateur, soit aucune (fiche
     * de club « non assignée », que l'on affectera ensuite via l'effectif de saison).
     */
    public Joueur create(Joueur joueur, UUID equipeSouhaitee) {
        if (joueur.getClubId() == null) {
            joueur.setClubId(scopeResolver.clubActif());
        }
        // Phase 4 : l'équipe n'est plus un champ de la fiche — elle ne sert qu'à inscrire à l'effectif.
        UUID equipeId = equipeSouhaitee != null ? equipeSouhaitee : scopeResolver.equipePourEcriture();
        if (equipeId != null) {
            scopeResolver.verifieAcces(equipeId); // l'équipe doit être dans la portée du créateur
        }
        Joueur saved = joueurRepository.save(joueur);
        inscrireEffectifSaison(saved, equipeId);
        return saved;
    }

    /**
     * Cohérence dashboard ↔ effectif : une fiche assignée à une équipe est inscrite à l'effectif de
     * la saison EN_COURS (si elle existe), pour apparaître dans l'effectif de saison ET le dashboard.
     * Sans équipe (fiche « non assignée ») ou sans saison ouverte : aucune inscription.
     */
    private void inscrireEffectifSaison(Joueur joueur, UUID equipeId) {
        if (equipeId == null || joueur.getClubId() == null) return;
        Saison saison = saisonRepository.findFirstByClubIdAndStatut(joueur.getClubId(), "EN_COURS").orElse(null);
        if (saison == null) return;
        if (effectifRepository.existsBySaisonIdAndEquipeIdAndJoueurId(saison.getId(), equipeId, joueur.getId())) return;
        EffectifSaison m = new EffectifSaison();
        m.setSaisonId(saison.getId());
        m.setEquipeId(equipeId);
        m.setJoueurId(joueur.getId());
        m.setDateEntree(saison.getDateDebut());
        effectifRepository.save(m);
    }

    /**
     * Inscrit la fiche à l'effectif (saison EN_COURS du club, équipe donnée) si elle n'appartient
     * encore à AUCUN effectif de cette saison — une fiche qui reçoit des données doit être visible
     * dans les écrans pilotés par l'effectif. Ne touche jamais un joueur déjà inscrit dans une
     * autre équipe (multi-équipes / prêt interne). No-op sans saison ouverte ou sans équipe.
     */
    public void inscrireEffectifSiHorsSaison(Joueur joueur, UUID equipeId) {
        if (equipeId == null || joueur.getClubId() == null) return;
        Saison saison = saisonRepository.findFirstByClubIdAndStatut(joueur.getClubId(), "EN_COURS").orElse(null);
        if (saison == null) return;
        if (effectifRepository.existsBySaisonIdAndJoueurId(saison.getId(), joueur.getId())) return;
        EffectifSaison m = new EffectifSaison();
        m.setSaisonId(saison.getId());
        m.setEquipeId(equipeId);
        m.setJoueurId(joueur.getId());
        m.setDateEntree(saison.getDateDebut());
        effectifRepository.save(m);
    }

    /** Toutes les fiches (y compris inactives), limitées à la portée. Club entier pour les rôles club-wide. */
    public List<Joueur> findAllPlayers() {
        UUID club = scopeResolver.clubEntierPourGestion();
        if (club != null) return joueurRepository.findJoueursByClub(club);
        Scope s = scopeResolver.resolve();
        // Même garde que findAll() : jamais la plateforme entière pour un super-admin hors contexte.
        if (s.all()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Sélectionnez un club");
        if (s.none()) return List.of();
        return joueurRepository.findByEquipeIdIn(s.equipeIds());
    }

    public void deleteById(UUID id) {
        joueurRepository.deleteById(id);
    }

    /**
     * Annuaire du club : toutes les personnes (fiches joueur, le staff est déjà exclu par la
     * dérivation Phase 2) avec leurs équipes d'appartenance issues de l'effectif de la saison
     * EN_COURS. Une fiche sans aucune équipe est « non assignée » (pool). Scope = celui de
     * {@link #findAllPlayers()}.
     */
    public List<AnnuaireJoueurDto> annuaire() {
        List<Joueur> personnes = findAllPlayers();
        UUID club = scopeResolver.clubActif();
        Map<UUID, String> nomEquipe = equipeRepository.findByClubId(club).stream()
                .collect(Collectors.toMap(Equipe::getId, Equipe::getNom));
        Map<UUID, List<UUID>> equipesParJoueur = new HashMap<>();
        saisonRepository.findFirstByClubIdAndStatut(club, "EN_COURS").ifPresent(s ->
                effectifRepository.findBySaisonId(s.getId()).forEach(m ->
                        equipesParJoueur.computeIfAbsent(m.getJoueurId(), k -> new ArrayList<>())
                                .add(m.getEquipeId())));
        return personnes.stream()
                .map(j -> {
                    List<EquipeRef> refs = equipesParJoueur.getOrDefault(j.getId(), List.of()).stream()
                            .distinct()
                            .map(id -> new EquipeRef(id, nomEquipe.get(id)))
                            .toList();
                    return new AnnuaireJoueurDto(j.getId(), j.getNom(), j.getPrenom(),
                            j.getPostePrincipal(), refs, !refs.isEmpty());
                })
                .sorted(Comparator.comparing((AnnuaireJoueurDto a) -> a.nom() == null ? "" : a.nom(),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(a -> a.prenom() == null ? "" : a.prenom(), String.CASE_INSENSITIVE_ORDER))
                .toList();
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
