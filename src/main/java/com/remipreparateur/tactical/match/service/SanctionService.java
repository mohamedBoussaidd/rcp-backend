package com.remipreparateur.tactical.match.service;

import com.remipreparateur.club.repository.ConfigParamRepository;
import com.remipreparateur.club.repository.EquipeRepository;
import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.saison.repository.SaisonRepository;
import com.remipreparateur.shared.security.ScopeResolver;
import com.remipreparateur.shared.time.Horloge;
import com.remipreparateur.tactical.match.dto.SanctionDtos.*;
import com.remipreparateur.tactical.match.entity.MatchCompo;
import com.remipreparateur.tactical.match.entity.MatchPrepa;
import com.remipreparateur.tactical.match.entity.MatchSuspendu;
import com.remipreparateur.tactical.match.repository.MatchCompoRepository;
import com.remipreparateur.tactical.match.repository.MatchPrepaRepository;
import com.remipreparateur.tactical.match.repository.MatchSuspenduRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cumul de cartons et risque de suspension (module add-on {@code stats_competition}).
 *
 * <p><b>L'application compte, la commission suspend.</b> Ce service ne décide rien et n'écrit
 * rien : il lit la feuille de match et renvoie un état, que l'écran de composition affiche sous
 * forme de badge. La déclaration reste un geste du staff, sur la table {@code match_suspendu} qui
 * existe depuis le Match v2 — aucune table de sanctions n'a été créée, et c'est délibéré : la
 * durée réellement prononcée par la commission viendra de la fédération, pas d'une déduction.
 *
 * <p><b>Ce que la règle a de non évident :</b>
 * <ul>
 *   <li>le décompte est <i>par type de match</i> — championnat et coupe ne se mélangent pas, et un
 *       amical ne compte jamais ;</li>
 *   <li>deux avertissements dans la même rencontre valent expulsion : ils sont absorbés par le
 *       rouge et ne comptent pas au cumul, sans quoi un match en vaudrait deux ;</li>
 *   <li>la remise à zéro après purge se lit sur {@code match_suspendu} : on ne compte que ce qui
 *       s'est passé après le dernier match purgé. Sans cela, un badge resterait allumé à vie.</li>
 * </ul>
 *
 * <p>Le seuil vit en configuration ({@code sanctions_seuil_jaunes}) parce qu'il varie d'un district
 * à l'autre : le coder en dur produirait des alertes fausses, et une alerte fausse est pire que
 * pas d'alerte — elle apprend au staff à ne plus les lire.
 */
@Service
public class SanctionService {

    private static final String CLE_SEUIL_JAUNES = "sanctions_seuil_jaunes";
    private static final int SEUIL_DEFAUT = 3;

    private final MatchPrepaRepository matchRepository;
    private final MatchCompoRepository compoRepository;
    private final MatchSuspenduRepository suspenduRepository;
    private final JoueurRepository joueurRepository;
    private final EquipeRepository equipeRepository;
    private final SaisonRepository saisonRepository;
    private final ConfigParamRepository configRepository;
    private final ScopeResolver scopeResolver;
    private final Horloge horloge;

    public SanctionService(MatchPrepaRepository matchRepository,
                           MatchCompoRepository compoRepository,
                           MatchSuspenduRepository suspenduRepository,
                           JoueurRepository joueurRepository,
                           EquipeRepository equipeRepository,
                           SaisonRepository saisonRepository,
                           ConfigParamRepository configRepository,
                           ScopeResolver scopeResolver,
                           Horloge horloge) {
        this.matchRepository = matchRepository;
        this.compoRepository = compoRepository;
        this.suspenduRepository = suspenduRepository;
        this.joueurRepository = joueurRepository;
        this.equipeRepository = equipeRepository;
        this.saisonRepository = saisonRepository;
        this.configRepository = configRepository;
        this.scopeResolver = scopeResolver;
        this.horloge = horloge;
    }

    @Transactional(readOnly = true)
    public SanctionsMatch pourMatch(UUID matchId) {
        MatchPrepa match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match introuvable"));
        scopeResolver.verifieAcces(match.getEquipeId());

        int seuil = seuil();
        LocalDate debutSaison = debutSaison(match);

        // Un amical ne compte pas : on le dit explicitement plutôt que de renvoyer une liste vide,
        // que l'écran ne saurait pas distinguer d'un effectif sans le moindre carton.
        if ("AMICAL".equals(match.getTypeMatch())) {
            return new SanctionsMatch(match.getId(), match.getTypeMatch(), false, seuil, debutSaison, List.of());
        }

        // Une rencontre déjà jouée ne s'alerte pas : il n'y a plus rien à décider, et rouvrir une
        // vieille compo pour la consulter faisait apparaître une suspension née APRÈS ce match-là.
        // Une suspension ne se purge que sur un match à venir.
        if (match.getDateMatch() != null && match.getDateMatch().isBefore(horloge.today())) {
            return new SanctionsMatch(match.getId(), match.getTypeMatch(), false, seuil, debutSaison, List.of());
        }

        // Les rencontres qui alimentent le cumul : même équipe, même type, dans la saison, et
        // ANTÉRIEURES à celle qu'on prépare — un match à venir ne sanctionne personne.
        List<MatchPrepa> anterieurs = matchRepository
                .findByEquipeIdOrderByDateMatchDescCreatedAtDesc(match.getEquipeId()).stream()
                .filter(m -> !m.getId().equals(match.getId()))
                .filter(m -> Objects.equals(m.getTypeMatch(), match.getTypeMatch()))
                .filter(m -> m.getDateMatch() != null && !m.getDateMatch().isBefore(debutSaison))
                // STRICTEMENT antérieures : `!isAfter` laissait passer les rencontres du même jour,
                // qui auraient sanctionné un joueur pour un carton pris quelques heures plus tard.
                .filter(m -> match.getDateMatch() == null || m.getDateMatch().isBefore(match.getDateMatch()))
                .toList();
        if (anterieurs.isEmpty()) {
            return new SanctionsMatch(match.getId(), match.getTypeMatch(), true, seuil, debutSaison, List.of());
        }

        List<UUID> idsAnterieurs = anterieurs.stream().map(MatchPrepa::getId).toList();
        Map<UUID, LocalDate> dateParMatch = anterieurs.stream()
                .collect(Collectors.toMap(MatchPrepa::getId, MatchPrepa::getDateMatch, (a, b) -> a));

        // Dernière suspension purgée par joueur : c'est elle qui remet le compteur à zéro.
        Map<UUID, LocalDate> purgeParJoueur = new HashMap<>();
        for (MatchSuspendu s : suspenduRepository.findByMatchIdIn(idsAnterieurs)) {
            LocalDate d = dateParMatch.get(s.getMatchId());
            if (d == null) continue;
            purgeParJoueur.merge(s.getJoueurId(), d, (a, b) -> a.isAfter(b) ? a : b);
        }

        Set<UUID> dejaDeclares = suspenduRepository.findByMatchId(match.getId()).stream()
                .map(MatchSuspendu::getJoueurId).collect(Collectors.toSet());

        Map<UUID, Cumul> cumuls = new HashMap<>();
        for (MatchCompo c : compoRepository.findByMatchIdIn(idsAnterieurs)) {
            LocalDate date = dateParMatch.get(c.getMatchId());
            if (date == null) continue;
            LocalDate purge = purgeParJoueur.get(c.getJoueurId());
            if (purge != null && !date.isAfter(purge)) continue;   // antérieur à la purge : effacé

            Cumul cu = cumuls.computeIfAbsent(c.getJoueurId(), k -> new Cumul());
            if (c.isCartonRouge()) {
                // L'expulsion la plus récente prime : c'est celle qui n'est pas encore purgée.
                if (cu.dateExpulsion == null || date.isAfter(cu.dateExpulsion)) cu.dateExpulsion = date;
            } else if (c.getCartonsJaunes() == 1) {
                cu.avertissements++;
            }
        }
        if (cumuls.isEmpty()) {
            return new SanctionsMatch(match.getId(), match.getTypeMatch(), true, seuil, debutSaison, List.of());
        }

        Map<UUID, Joueur> joueurs = joueurRepository.findAllById(cumuls.keySet()).stream()
                .collect(Collectors.toMap(Joueur::getId, Function.identity()));

        List<EtatSanction> etats = cumuls.entrySet().stream()
                .map(e -> etat(e.getKey(), e.getValue(), seuil, joueurs.get(e.getKey()), dejaDeclares))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(EtatSanction::expulse).reversed()
                        .thenComparing(Comparator.comparingInt(EtatSanction::avertissements).reversed()))
                .toList();

        return new SanctionsMatch(match.getId(), match.getTypeMatch(), true, seuil, debutSaison, etats);
    }

    /** Rien à signaler = pas de ligne : l'écran n'a pas à filtrer un effectif entier pour trois badges. */
    private EtatSanction etat(UUID joueurId, Cumul cu, int seuil, Joueur j, Set<UUID> dejaDeclares) {
        boolean expulse = cu.dateExpulsion != null;
        boolean seuilAtteint = cu.avertissements >= seuil;
        boolean sousLaMenace = !seuilAtteint && cu.avertissements == seuil - 1;
        if (!expulse && !seuilAtteint && !sousLaMenace) return null;

        return new EtatSanction(joueurId,
                j != null ? j.getNom() : null,
                j != null ? j.getPrenom() : null,
                cu.avertissements, seuil, seuilAtteint, sousLaMenace, expulse, cu.dateExpulsion,
                dejaDeclares.contains(joueurId),
                libelle(cu, seuil, expulse, seuilAtteint));
    }

    private String libelle(Cumul cu, int seuil, boolean expulse, boolean seuilAtteint) {
        if (expulse) {
            return "Expulsé le " + cu.dateExpulsion + " — un match ferme au minimum, en attente de la commission.";
        }
        if (seuilAtteint) {
            return cu.avertissements + " avertissements sur " + seuil
                    + " — suspension à purger au prochain match officiel.";
        }
        return cu.avertissements + " avertissements sur " + seuil
                + " — un carton jaune de plus et il est suspendu.";
    }

    /**
     * Fenêtre de comptage. La saison en cours du club fait foi ; à défaut (club sans saison
     * ouverte) on retombe sur douze mois glissants, qui vaut mieux qu'un cumul depuis toujours.
     */
    private LocalDate debutSaison(MatchPrepa match) {
        LocalDate reference = match.getDateMatch() != null ? match.getDateMatch() : LocalDate.now();
        return equipeRepository.findById(match.getEquipeId())
                .flatMap(e -> saisonRepository.findFirstByClubIdAndStatut(e.getClubId(), "EN_COURS"))
                .map(s -> s.getDateDebut())
                .orElse(reference.minusYears(1));
    }

    private int seuil() {
        return configRepository.findById(CLE_SEUIL_JAUNES)
                .map(p -> p.getValeur().intValue())
                .filter(v -> v > 0)
                .orElse(SEUIL_DEFAUT);
    }

    /** Accumulateur de travail : un joueur, ses avertissements et sa dernière expulsion. */
    private static final class Cumul {
        int avertissements;
        LocalDate dateExpulsion;
    }
}
