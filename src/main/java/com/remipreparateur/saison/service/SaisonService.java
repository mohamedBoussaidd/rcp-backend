package com.remipreparateur.saison.service;

import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.auth.repository.UtilisateurRepository;
import com.remipreparateur.club.entity.Equipe;
import com.remipreparateur.club.repository.EquipeRepository;
import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.medical.blessure.entity.Blessure;
import com.remipreparateur.medical.blessure.repository.BlessureRepository;
import com.remipreparateur.performance.seance.repository.SeanceRepository;
import com.remipreparateur.saison.dto.SaisonDtos.*;
import com.remipreparateur.saison.entity.EffectifSaison;
import com.remipreparateur.saison.entity.PeriodeSaison;
import com.remipreparateur.saison.entity.Saison;
import com.remipreparateur.saison.repository.EffectifSaisonRepository;
import com.remipreparateur.saison.repository.PeriodeSaisonRepository;
import com.remipreparateur.saison.repository.SaisonRepository;
import com.remipreparateur.shared.security.Scope;
import com.remipreparateur.shared.security.ScopeResolver;
import com.remipreparateur.shared.time.Horloge;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Gestion des saisons d'un CLUB : ouverture (clôture automatique de la précédente),
 * clôture, périodes typées (génération par défaut + édition libre) et effectif de saison,
 * ces deux derniers étant définis PAR ÉQUIPE à l'intérieur de la saison (cf. V37).
 *
 * Important :
 *  - la clôture d'une saison NE solde PAS les blessures en cours — une blessure longue suit
 *    le joueur dans la saison suivante (cf. scheduler de retour) ;
 *  - lors de la reconduction d'effectif, un joueur écarté de la nouvelle saison voit son
 *    accès PWA coupé (compte désactivé) ; un joueur réintégré le retrouve.
 */
@Service
@RequiredArgsConstructor
public class SaisonService {

    private final SaisonRepository saisonRepository;
    private final PeriodeSaisonRepository periodeRepository;
    private final EffectifSaisonRepository effectifRepository;
    private final JoueurRepository joueurRepository;
    private final BlessureRepository blessureRepository;
    private final SeanceRepository seanceRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final EquipeRepository equipeRepository;
    private final ScopeResolver scopeResolver;
    private final Horloge horloge;

    // ══════════ Lecture ══════════

    public List<SaisonDto> findAll() {
        // resolve() valide le contexte (403 si hors périmètre). On ne bail PLUS sur s.none() : un
        // président/administratif d'un club sans équipe doit voir les saisons de SON club (la saison
        // est au niveau club, pas équipe).
        Scope s = scopeResolver.resolve();
        UUID equipeId = equipeActiveOuNull();
        UUID club = clubActifOuNull();
        List<Saison> saisons;
        if (club != null)      saisons = saisonRepository.findByClubIdOrderByDateDebutDesc(club);
        else if (s.all())      saisons = saisonRepository.findAll().stream()
                .sorted(Comparator.comparing(Saison::getDateDebut).reversed()).toList();
        else                   return List.of();
        LocalDate now = horloge.today();
        return saisons.stream().map(x -> toDto(x, now, equipeId)).collect(Collectors.toList());
    }

    public SaisonDto get(UUID id) {
        return toDto(charge(id), horloge.today(), equipeActiveOuNull());
    }

    /** Saison EN_COURS du club actif (pour le bandeau de période). null si aucune. */
    public SaisonDto courante() {
        return courante(null);
    }

    /**
     * Variante avec date de référence (date simulée pour tester la temporalité) : la période
     * courante est calculée contre {@code dateRef} (défaut = aujourd'hui).
     */
    public SaisonDto courante(LocalDate dateRef) {
        UUID club = clubActifOuNull();
        if (club == null) return null;
        LocalDate ref = dateRef != null ? dateRef : horloge.today();
        UUID equipeId = equipeActiveOuNull();
        return saisonRepository.findFirstByClubIdAndStatut(club, "EN_COURS")
                .map(s -> toDto(s, ref, equipeId)).orElse(null);
    }

    // ══════════ Écriture saison ══════════

    @Transactional
    public SaisonDto ouvrir(SaisonRequest req) {
        UUID club = scopeResolver.clubActif();
        valider(req);
        String statut = (req.statut() == null || req.statut().isBlank()) ? "EN_COURS" : req.statut().trim();

        // Clôture la saison EN_COURS précédente (flush immédiat avant l'insert pour respecter
        // l'index unique partiel « une seule EN_COURS par club »). Les blessures NE sont PAS touchées.
        if ("EN_COURS".equals(statut)) {
            saisonRepository.findFirstByClubIdAndStatut(club, "EN_COURS").ifPresent(prev -> {
                prev.setStatut("CLOTUREE");
                saisonRepository.saveAndFlush(prev);
            });
        }

        Saison s = new Saison();
        s.setClubId(club);
        s.setLibelle(req.libelle().trim());
        s.setDateDebut(req.dateDebut());
        s.setDateFin(req.dateFin());
        s.setStatut(statut);
        s = saisonRepository.save(s);

        // Périodes par défaut générées pour l'équipe du staff qui ouvre la saison (les autres
        // équipes du club généreront/ajusteront les leurs depuis leur propre contexte).
        UUID equipeId = equipeActiveOuNull();
        if (req.genererPeriodes() && equipeId != null) {
            for (PeriodeSaison p : genererPeriodesDefaut(s, equipeId)) periodeRepository.save(p);
        }
        return toDto(charge(s.getId()), horloge.today(), equipeId);
    }

    @Transactional
    public SaisonDto update(UUID id, SaisonRequest req) {
        Saison s = charge(id);
        valider(req);
        s.setLibelle(req.libelle().trim());
        s.setDateDebut(req.dateDebut());
        s.setDateFin(req.dateFin());
        if (req.statut() != null && !req.statut().isBlank()) {
            String nv = req.statut().trim();
            // Réactiver une saison en EN_COURS : clôturer l'autre EN_COURS du club d'abord.
            if ("EN_COURS".equals(nv) && !"EN_COURS".equals(s.getStatut())) {
                saisonRepository.findFirstByClubIdAndStatut(s.getClubId(), "EN_COURS").ifPresent(prev -> {
                    if (!prev.getId().equals(id)) { prev.setStatut("CLOTUREE"); saisonRepository.saveAndFlush(prev); }
                });
            }
            s.setStatut(nv);
        }
        return toDto(saisonRepository.save(s), horloge.today(), equipeActiveOuNull());
    }

    @Transactional
    public SaisonDto cloturer(UUID id) {
        Saison s = charge(id);
        s.setStatut("CLOTUREE");   // ne touche pas aux blessures : elles suivent le joueur
        return toDto(saisonRepository.save(s), horloge.today(), equipeActiveOuNull());
    }

    @Transactional
    public void delete(UUID id) {
        Saison s = charge(id);
        periodeRepository.deleteBySaisonId(s.getId());   // FK ON DELETE CASCADE couvre aussi, ceinture+bretelles
        effectifRepository.deleteBySaisonId(s.getId());
        saisonRepository.deleteById(s.getId());
    }

    // ══════════ Périodes (par équipe) ══════════

    @Transactional
    public SaisonDto genererPeriodesParDefaut(UUID id) {
        Saison s = charge(id);
        UUID equipeId = scopeResolver.equipeActiveUnique();
        periodeRepository.deleteBySaisonIdAndEquipeId(s.getId(), equipeId);
        for (PeriodeSaison p : genererPeriodesDefaut(s, equipeId)) periodeRepository.save(p);
        return toDto(charge(id), horloge.today(), equipeId);
    }

    @Transactional
    public SaisonDto remplacerPeriodes(UUID id, PeriodesRequest req) {
        Saison s = charge(id);
        UUID equipeId = scopeResolver.equipeActiveUnique();
        periodeRepository.deleteBySaisonIdAndEquipeId(s.getId(), equipeId);
        if (req.periodes() != null) {
            short ordre = 0;
            for (PeriodeDto dto : req.periodes()) {
                if (!TYPES.contains(dto.type())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type de période invalide : " + dto.type());
                }
                if (dto.dateDebut() == null || dto.dateFin() == null || dto.dateFin().isBefore(dto.dateDebut())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dates de période invalides");
                }
                PeriodeSaison p = new PeriodeSaison();
                p.setSaisonId(s.getId());
                p.setEquipeId(equipeId);
                p.setType(dto.type());
                p.setLibelle((dto.libelle() == null || dto.libelle().isBlank()) ? libelleDefaut(dto.type()) : dto.libelle().trim());
                p.setDateDebut(dto.dateDebut());
                p.setDateFin(dto.dateFin());
                p.setOrdre(dto.ordre() != 0 ? dto.ordre() : ordre);
                periodeRepository.save(p);
                ordre++;
            }
        }
        return toDto(charge(id), horloge.today(), equipeId);
    }

    // ══════════ Effectif (par équipe) ══════════

    public List<EffectifMembreDto> effectif(UUID id) {
        Saison s = charge(id);
        UUID equipeId = scopeResolver.equipeActiveUnique();
        return effectifDto(s.getId(), equipeId);
    }

    /**
     * Définit l'effectif de l'équipe active (remplacement complet) à partir d'une liste de joueurs.
     * Si la saison est EN_COURS, l'accès PWA des comptes joueur est synchronisé : un joueur écarté
     * (présent dans l'effectif courant OU dans celui de la saison précédente, mais absent de la
     * nouvelle liste) voit son accès coupé ; un joueur retenu le retrouve.
     */
    @Transactional
    public List<EffectifMembreDto> definirEffectif(UUID id, EffectifRequest req) {
        Saison s = charge(id);
        UUID equipeId = scopeResolver.equipeActiveUnique();

        Set<UUID> avant = effectifRepository.findBySaisonIdAndEquipeId(s.getId(), equipeId)
                .stream().map(EffectifSaison::getJoueurId).collect(Collectors.toCollection(HashSet::new));
        Set<UUID> apres = (req.joueurIds() == null) ? new HashSet<>()
                : req.joueurIds().stream().filter(j -> j != null).collect(Collectors.toCollection(HashSet::new));

        effectifRepository.deleteBySaisonIdAndEquipeId(s.getId(), equipeId);
        for (UUID jid : apres) ajouterMembre(s, equipeId, jid);

        if ("EN_COURS".equals(s.getStatut())) {
            // Référence des « anciens » membres = effectif courant + effectif de la saison précédente
            // (couvre la 1re définition d'une nouvelle saison où l'effectif courant part vide).
            Set<UUID> reference = new HashSet<>(avant);
            saisonRepository.findFirstByClubIdAndIdNotOrderByDateFinDesc(s.getClubId(), s.getId())
                    .ifPresent(prec -> effectifRepository.findBySaisonIdAndEquipeId(prec.getId(), equipeId)
                            .forEach(m -> reference.add(m.getJoueurId())));
            for (UUID jid : apres) reactiverComptePwa(jid);
            for (UUID jid : reference) if (!apres.contains(jid)) desactiverComptePwa(jid);
        }
        return effectifDto(s.getId(), equipeId);
    }

    /**
     * Annuaire : inscrit UNE personne à l'effectif de la saison EN_COURS d'UNE équipe (assignation
     * unitaire, sans remplacement de l'effectif). Réactive l'accès PWA du compte joueur lié.
     */
    @Transactional
    public void inscrireAEquipe(UUID joueurId, UUID equipeId) {
        scopeResolver.verifieAcces(equipeId);
        Joueur j = joueurRepository.findById(joueurId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fiche introuvable"));
        UUID club = equipeRepository.findById(equipeId).map(Equipe::getClubId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipe introuvable"));
        if (j.getClubId() != null && !j.getClubId().equals(club)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Fiche hors du club de l'equipe");
        }
        Saison s = saisonRepository.findFirstByClubIdAndStatut(club, "EN_COURS")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Aucune saison en cours"));
        if (!effectifRepository.existsBySaisonIdAndEquipeIdAndJoueurId(s.getId(), equipeId, joueurId)) {
            ajouterMembre(s, equipeId, joueurId);
        }
        reactiverComptePwa(joueurId);
    }

    /**
     * Annuaire : retire UNE personne de l'effectif de la saison EN_COURS d'UNE équipe. Si elle n'est
     * plus dans AUCUN effectif de la saison en cours, son accès PWA est coupé (cohérent definirEffectif).
     */
    @Transactional
    public void retirerDeEquipe(UUID joueurId, UUID equipeId) {
        scopeResolver.verifieAcces(equipeId);
        UUID club = equipeRepository.findById(equipeId).map(Equipe::getClubId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipe introuvable"));
        Saison s = saisonRepository.findFirstByClubIdAndStatut(club, "EN_COURS")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Aucune saison en cours"));
        effectifRepository.deleteBySaisonIdAndEquipeIdAndJoueurId(s.getId(), equipeId, joueurId);
        if (!effectifRepository.existsBySaisonIdAndJoueurId(s.getId(), joueurId)) {
            desactiverComptePwa(joueurId);
        }
    }

    /** Proposition de reconduction : joueurs de la saison précédente (même équipe), cochés par défaut. */
    public ReconductionProposition propositionReconduction(UUID id) {
        Saison s = charge(id);
        UUID equipeId = scopeResolver.equipeActiveUnique();
        Saison prec = saisonRepository.findFirstByClubIdAndIdNotOrderByDateFinDesc(s.getClubId(), s.getId())
                .orElse(null);
        if (prec == null) {
            return new ReconductionProposition(null, null, List.of());
        }
        List<EffectifSaison> precMembres = effectifRepository.findBySaisonIdAndEquipeId(prec.getId(), equipeId);
        Map<UUID, Joueur> joueurs = joueurRepository.findAllById(
                        precMembres.stream().map(EffectifSaison::getJoueurId).distinct().toList())
                .stream().collect(Collectors.toMap(Joueur::getId, Function.identity()));

        List<ReconductionLigne> lignes = precMembres.stream()
                .map(m -> {
                    Joueur j = joueurs.get(m.getJoueurId());
                    boolean blesse = blessureRepository.findByJoueurIdOrderByDateBlessureDesc(m.getJoueurId())
                            .stream().anyMatch(b -> !"RETABLI".equals(b.getStatut()));
                    return new ReconductionLigne(
                            m.getJoueurId(),
                            j != null ? j.getNom() : null,
                            j != null ? j.getPrenom() : null,
                            j != null ? j.getPostePrincipal() : null,
                            true, blesse);
                })
                .collect(Collectors.toList());
        return new ReconductionProposition(prec.getId(), prec.getLibelle(), lignes);
    }

    /**
     * Applique la reconduction : ajoute (sans doublon) les joueurs retenus à l'effectif de la
     * saison/équipe, RÉACTIVE leur accès PWA s'il était coupé, et coupe l'accès PWA des joueurs
     * de la saison précédente qui n'ont PAS été retenus (écartés).
     */
    @Transactional
    public ReconductionResultat appliquerReconduction(UUID id, EffectifRequest req) {
        Saison s = charge(id);
        UUID equipeId = scopeResolver.equipeActiveUnique();
        Set<UUID> retenus = (req.joueurIds() == null) ? Set.of() : new HashSet<>(req.joueurIds());

        int reactives = 0;
        for (UUID jid : retenus) {
            if (!effectifRepository.existsBySaisonIdAndEquipeIdAndJoueurId(s.getId(), equipeId, jid)) {
                ajouterMembre(s, equipeId, jid);
            }
            reactives += reactiverComptePwa(jid);   // un joueur réintégré retrouve son accès PWA
        }

        int desactives = 0;
        Saison prec = saisonRepository.findFirstByClubIdAndIdNotOrderByDateFinDesc(s.getClubId(), s.getId())
                .orElse(null);
        if (prec != null) {
            for (EffectifSaison m : effectifRepository.findBySaisonIdAndEquipeId(prec.getId(), equipeId)) {
                if (!retenus.contains(m.getJoueurId())) {
                    desactives += desactiverComptePwa(m.getJoueurId());   // écarté → accès PWA coupé
                }
            }
        }
        return new ReconductionResultat(effectifDto(s.getId(), equipeId), desactives, reactives);
    }

    // ══════════ Bilan (comparaison inter-saisons, par équipe) ══════════

    public BilanSaison bilan(UUID id) {
        Saison s = charge(id);
        UUID equipeId = scopeResolver.equipeActiveUnique();
        int effectif = effectifRepository.countBySaisonIdAndEquipeId(s.getId(), equipeId);
        int nbSeances = seanceRepository.findByDateBetweenAndEquipeIdInOrderByDateAscHeureDebutAsc(
                s.getDateDebut(), s.getDateFin(), List.of(equipeId)).size();
        int nbBlessures = (int) blessureRepository.findByEquipeIdInOrderByDateBlessureDesc(List.of(equipeId))
                .stream()
                .map(Blessure::getDateBlessure)
                .filter(d -> d != null && !d.isBefore(s.getDateDebut()) && !d.isAfter(s.getDateFin()))
                .count();
        int jours = (int) (ChronoUnit.DAYS.between(s.getDateDebut(), s.getDateFin()) + 1);
        return new BilanSaison(s.getId(), s.getLibelle(), s.getStatut(),
                s.getDateDebut(), s.getDateFin(), jours, effectif, nbSeances, nbBlessures);
    }

    // ══════════ Helpers ══════════

    private static final List<String> TYPES =
            List.of("PREPARATION", "COMPETITION", "TREVE", "REPRISE", "INTERSAISON");

    /** Équipe active unique, ou null si non résolue (président/super-admin sans équipe ciblée). */
    private UUID equipeActiveOuNull() {
        try { return scopeResolver.equipeActiveUnique(); }
        catch (ResponseStatusException e) { return null; }
    }

    /** Club actif, ou null si indéterminé (super-admin sans contexte). */
    private UUID clubActifOuNull() {
        try { return scopeResolver.clubActif(); }
        catch (ResponseStatusException e) { return null; }
    }

    private List<EffectifMembreDto> effectifDto(UUID saisonId, UUID equipeId) {
        List<EffectifSaison> membres = effectifRepository.findBySaisonIdAndEquipeId(saisonId, equipeId);
        Map<UUID, Joueur> joueurs = joueurRepository.findAllById(
                        membres.stream().map(EffectifSaison::getJoueurId).distinct().toList())
                .stream().collect(Collectors.toMap(Joueur::getId, Function.identity()));
        return membres.stream()
                .map(m -> {
                    Joueur j = joueurs.get(m.getJoueurId());
                    return new EffectifMembreDto(
                            m.getJoueurId(),
                            j != null ? j.getNom() : null,
                            j != null ? j.getPrenom() : null,
                            j != null ? j.getPostePrincipal() : null,
                            j != null ? j.getStatut() : null,
                            m.getNumeroMaillot(), m.getDateEntree(), m.getDateSortie());
                })
                .sorted((a, b) -> {
                    String an = (a.nom() == null ? "" : a.nom());
                    String bn = (b.nom() == null ? "" : b.nom());
                    return an.compareToIgnoreCase(bn);
                })
                .collect(Collectors.toList());
    }

    private void ajouterMembre(Saison s, UUID equipeId, UUID joueurId) {
        Joueur j = joueurRepository.findById(joueurId).orElse(null);
        if (j == null) return;   // ignore les ids inconnus
        EffectifSaison m = new EffectifSaison();
        m.setSaisonId(s.getId());
        m.setEquipeId(equipeId);
        m.setJoueurId(joueurId);
        m.setDateEntree(s.getDateDebut());
        effectifRepository.save(m);
        // Phase 4 : plus de cache joueur.equipe_id à maintenir. Une fiche sans club adopte le club
        // de la saison (rattachement au niveau club, source de vérité pour l'accès).
        if (j.getClubId() == null) {
            j.setClubId(s.getClubId());
            joueurRepository.save(j);
        }
    }

    /** Coupe l'accès PWA du joueur (compte désactivé). Retourne 1 si un compte actif a été coupé. */
    private int desactiverComptePwa(UUID joueurId) {
        return utilisateurRepository.findByJoueurId(joueurId)
                .filter(Utilisateur::isActif)
                .map(u -> { u.setActif(false); utilisateurRepository.save(u); return 1; })
                .orElse(0);
    }

    /** Rouvre l'accès PWA du joueur. Retourne 1 si un compte désactivé a été réactivé. */
    private int reactiverComptePwa(UUID joueurId) {
        return utilisateurRepository.findByJoueurId(joueurId)
                .filter(u -> !u.isActif())
                .map(u -> { u.setActif(true); utilisateurRepository.save(u); return 1; })
                .orElse(0);
    }

    private Saison charge(UUID id) {
        Saison s = saisonRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saison introuvable"));
        scopeResolver.verifieAccesClub(s.getClubId());
        return s;
    }

    private void valider(SaisonRequest req) {
        if (req.libelle() == null || req.libelle().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le libellé de la saison est obligatoire");
        }
        if (req.dateDebut() == null || req.dateFin() == null || req.dateFin().isBefore(req.dateDebut())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dates de saison invalides");
        }
    }

    /** Période courante = celle qui contient la date de référence (null hors de toute période). */
    private PeriodeDto periodeCourante(List<PeriodeDto> periodes, LocalDate ref) {
        return periodes.stream()
                .filter(p -> !ref.isBefore(p.dateDebut()) && !ref.isAfter(p.dateFin()))
                .findFirst().orElse(null);
    }

    private String libelleDefaut(String type) {
        return switch (type) {
            case "PREPARATION" -> "Préparation";
            case "COMPETITION" -> "Championnat";
            case "TREVE"       -> "Trêve hivernale";
            case "REPRISE"     -> "Reprise";
            case "INTERSAISON" -> "Intersaison";
            default            -> type;
        };
    }

    private PeriodeSaison periode(Saison s, UUID equipeId, String type, LocalDate debut, LocalDate fin, short ordre) {
        PeriodeSaison p = new PeriodeSaison();
        p.setSaisonId(s.getId());
        p.setEquipeId(equipeId);
        p.setType(type);
        p.setLibelle(libelleDefaut(type));
        p.setDateDebut(debut);
        p.setDateFin(fin);
        p.setOrdre(ordre);
        return p;
    }

    /**
     * Calendrier par défaut déduit des dates : Préparation (~6 sem.) → Championnat →
     * Trêve hivernale (~2 sem. autour du 20 déc.) → Reprise (~2 sem.) → Championnat.
     * Entièrement modifiable ensuite via l'édition des périodes.
     */
    private List<PeriodeSaison> genererPeriodesDefaut(Saison s, UUID equipeId) {
        List<PeriodeSaison> out = new ArrayList<>();
        LocalDate debut = s.getDateDebut();
        LocalDate fin = s.getDateFin();
        short ordre = 0;

        LocalDate prepaFin = debut.plusWeeks(6).minusDays(1);
        if (prepaFin.isAfter(fin)) prepaFin = fin;
        out.add(periode(s, equipeId, "PREPARATION", debut, prepaFin, ordre++));

        LocalDate treveDebut = trouveTreve(debut, fin, prepaFin);
        if (treveDebut != null) {
            LocalDate treveFin = treveDebut.plusDays(13);
            if (treveFin.isAfter(fin)) treveFin = fin;
            LocalDate repriseFin = treveFin.plusDays(13);
            if (repriseFin.isAfter(fin)) repriseFin = fin;

            if (prepaFin.plusDays(1).isBefore(treveDebut)) {
                out.add(periode(s, equipeId, "COMPETITION", prepaFin.plusDays(1), treveDebut.minusDays(1), ordre++));
            }
            out.add(periode(s, equipeId, "TREVE", treveDebut, treveFin, ordre++));
            if (treveFin.isBefore(fin)) {
                out.add(periode(s, equipeId, "REPRISE", treveFin.plusDays(1), repriseFin, ordre++));
            }
            if (repriseFin.isBefore(fin)) {
                out.add(periode(s, equipeId, "COMPETITION", repriseFin.plusDays(1), fin, ordre++));
            }
        } else if (prepaFin.isBefore(fin)) {
            out.add(periode(s, equipeId, "COMPETITION", prepaFin.plusDays(1), fin, ordre++));
        }
        return out;
    }

    /** 20 décembre situé dans la plage (après la prépa, avant la fin), sinon null. */
    private LocalDate trouveTreve(LocalDate debut, LocalDate fin, LocalDate prepaFin) {
        for (int year = debut.getYear(); year <= fin.getYear(); year++) {
            LocalDate cand = LocalDate.of(year, 12, 20);
            if (!cand.isBefore(prepaFin.plusDays(1)) && cand.isBefore(fin)) {
                return cand;
            }
        }
        return null;
    }

    private SaisonDto toDto(Saison s, LocalDate ref, UUID equipeId) {
        List<PeriodeDto> periodes = equipeId == null ? List.of()
                : periodeRepository.findBySaisonIdAndEquipeIdOrderByDateDebutAscOrdreAsc(s.getId(), equipeId)
                .stream()
                .map(p -> new PeriodeDto(p.getId(), p.getType(), p.getLibelle(),
                        p.getDateDebut(), p.getDateFin(), p.getOrdre()))
                .collect(Collectors.toList());
        int count = equipeId == null ? 0 : effectifRepository.countBySaisonIdAndEquipeId(s.getId(), equipeId);
        return new SaisonDto(s.getId(), s.getClubId(), equipeId, s.getLibelle(),
                s.getDateDebut(), s.getDateFin(), s.getStatut(),
                periodeCourante(periodes, ref), periodes, count);
    }
}
