package com.remipreparateur.performance.rpe.service;

import com.remipreparateur.performance.rpe.dto.RpeDtos.RpeRequest;
import com.remipreparateur.performance.rpe.dto.RpeDtos.RpeResponse;
import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.performance.rpe.entity.RpeSeance;
import com.remipreparateur.performance.seance.entity.Seance;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.notification.service.NotificationProducer;
import com.remipreparateur.performance.rpe.repository.RpeSeanceRepository;
import com.remipreparateur.performance.seance.repository.SeanceRepository;
import com.remipreparateur.saison.service.AppartenanceService;
import com.remipreparateur.shared.security.CurrentUserProvider;
import com.remipreparateur.shared.security.Scope;
import com.remipreparateur.shared.security.ScopeResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * RPE de séance (effort perçu). Une saisie par séance (upsert sur joueur+séance).
 * La séance référencée est physique ({@code seance}) ou technique ({@code seance_technique})
 * selon {@code seanceType} ; date et équipe sont résolues depuis la séance.
 *
 * <p>Depuis V91 le questionnaire post-séance porte aussi le plaisir, un commentaire libre et
 * une gêne localisée. La gêne suit le MÊME cycle de vie que celle du wellness (déclarée →
 * traitée → éventuellement rouverte) mais reste rattachée à sa séance : un joueur peut avoir
 * déclaré une gêne le matin dans son ressenti ET une autre le soir après l'entraînement.
 */
@Service
public class RpeService {

    private static final String TYPE_PHYSIQUE = "PHYSIQUE";
    private static final String TYPE_TECHNIQUE = "TECHNIQUE";

    private static final Set<String> MOMENTS_GENE = Set.of("EFFORT", "APRES", "REPOS");

    private final RpeSeanceRepository repository;
    private final JoueurRepository joueurRepository;
    private final SeanceRepository seanceRepository;
    private final ScopeResolver scopeResolver;
    private final AppartenanceService appartenance;
    private final CurrentUserProvider currentUser;
    private final NotificationProducer notificationProducer;

    public RpeService(RpeSeanceRepository repository, JoueurRepository joueurRepository,
                      SeanceRepository seanceRepository,
                      ScopeResolver scopeResolver, AppartenanceService appartenance,
                      CurrentUserProvider currentUser, NotificationProducer notificationProducer) {
        this.repository = repository;
        this.joueurRepository = joueurRepository;
        this.seanceRepository = seanceRepository;
        this.scopeResolver = scopeResolver;
        this.appartenance = appartenance;
        this.currentUser = currentUser;
        this.notificationProducer = notificationProducer;
    }

    public RpeResponse enregistrer(UUID joueurId, RpeRequest req) {
        // Séances unifiées : la RPE porte toujours sur une Seance. seanceType conservé en base
        // pour compat (défaut PHYSIQUE).
        String type = req.seanceType() == null || req.seanceType().isBlank()
                ? TYPE_PHYSIQUE : req.seanceType().trim().toUpperCase();
        Joueur joueur = joueurRepository.findById(joueurId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Fiche joueur introuvable"));

        // Résolution date + équipe + durée depuis la séance référencée.
        Seance s = seanceRepository.findById(req.seanceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Séance introuvable"));
        LocalDate date = s.getDate();
        UUID equipeSeance = s.getEquipeId();
        // Durée RÉELLEMENT effectuée : le joueur peut l'avoir raccourcie (sortie anticipée).
        // À défaut de saisie, on retombe sur la durée planifiée de la séance.
        Short duree = req.dureeMinutes() != null ? req.dureeMinutes() : s.getDureeMinutes();

        // Le joueur ne peut noter qu'une séance d'UNE de ses équipes (effectif EN_COURS, Phase 4).
        if (equipeSeance == null || !appartenance.equipesDe(joueur.getId()).contains(equipeSeance)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Séance hors de votre équipe");
        }

        RpeSeance r = repository.findByJoueurIdAndSeanceId(joueurId, req.seanceId())
                .orElseGet(RpeSeance::new);
        r.setJoueurId(joueurId);
        r.setEquipeId(equipeSeance);
        r.setSeanceId(req.seanceId());
        r.setSeanceType(type);
        r.setDate(date);
        r.setRpe(req.rpe());
        r.setDureeMinutes(duree);
        r.setCharge(duree != null ? req.rpe() * duree : null);
        r.setPlaisir(req.plaisir());
        r.setCommentaire(videEnNull(req.commentaire()));
        appliquerGene(r, req);
        RpeSeance saved = repository.save(r);
        // Gêne signalée → alerte URGENTE au staff médical (best-effort, n'interrompt pas la saisie),
        // exactement comme une gêne déclarée dans le ressenti quotidien.
        if (saved.getGeneZone() != null) {
            // Le lien mène à la séance concernée : une gêne d'entraînement se lit avec son
            // contexte (quelle séance, quelle intensité, qui d'autre s'est plaint).
            notificationProducer.geneDeclaree(equipeSeance, joueurId,
                    (joueur.getPrenom() + " " + joueur.getNom()).trim(),
                    saved.getGeneZone(), saved.getGeneIntensite(),
                    "/rpe?seance=" + req.seanceId());
        }
        return toResponse(saved, joueur, s);
    }

    /**
     * Applique la gêne de la requête. Zone vide = pas de gêne : on efface tout le signalement,
     * y compris son cycle de traitement (sinon une gêne retirée resterait « traitée » en base).
     * Une (re)déclaration remet la gêne à l'état actif — même règle que le wellness.
     */
    private void appliquerGene(RpeSeance r, RpeRequest req) {
        String zone = videEnNull(req.geneZone());
        String moment = videEnNull(req.geneMoment());
        if (moment != null && !MOMENTS_GENE.contains(moment.toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Moment de gêne invalide");
        }
        r.setGeneZone(zone);
        r.setGeneIntensite(zone != null ? req.geneIntensite() : null);
        r.setGeneMoment(zone != null && moment != null ? moment.toUpperCase() : null);
        r.setGeneTraitee(false);
        r.setGeneTraiteePar(null);
        r.setGeneTraiteeLe(null);
        r.setGeneResolution(null);
    }

    public List<RpeResponse> listerPourJoueur(UUID joueurId) {
        List<RpeSeance> rows = repository.findByJoueurIdOrderByDateDesc(joueurId);
        Joueur joueur = joueurRepository.findById(joueurId).orElse(null);
        Map<UUID, Seance> seances = seancesDe(rows);
        return rows.stream().map(r -> toResponse(r, joueur, seances.get(r.getSeanceId()))).toList();
    }

    public List<RpeResponse> listerPourStaff(UUID joueurId) {
        List<RpeSeance> rows;
        if (joueurId != null) {
            rows = repository.findByJoueurIdOrderByDateDesc(joueurId).stream()
                    .filter(r -> scopeResolver.peutAcceder(r.getEquipeId()))
                    .toList();
        } else {
            Scope s = scopeResolver.resolve();
            if (s.all()) rows = repository.findAllByOrderByDateDesc();
            else if (s.none()) rows = List.of();
            else rows = repository.findByEquipeIdInOrderByDateDesc(s.equipeIds());
        }
        Map<UUID, Joueur> joueurs = joueurRepository.findAllById(
                        rows.stream().map(RpeSeance::getJoueurId).distinct().toList())
                .stream().collect(Collectors.toMap(Joueur::getId, Function.identity()));
        Map<UUID, Seance> seances = seancesDe(rows);
        return rows.stream()
                .map(r -> toResponse(r, joueurs.get(r.getJoueurId()), seances.get(r.getSeanceId())))
                .toList();
    }

    /**
     * Marque la gêne d'un RPE comme traitée (staff). Scopée à l'équipe.
     * {@code resolution} = ARCHIVEE (archivage simple) ou CONVERTIE (convertie en blessure).
     * Pendant du {@code WellnessService.traiterGene} pour la seconde source de gênes.
     */
    public RpeResponse traiterGene(UUID rpeId, String resolution) {
        RpeSeance r = chargerAvecGene(rpeId, "Aucune gêne à traiter");
        String res = "CONVERTIE".equalsIgnoreCase(resolution) ? "CONVERTIE" : "ARCHIVEE";
        r.setGeneTraitee(true);
        r.setGeneTraiteePar(currentUser.current().getId());
        r.setGeneTraiteeLe(LocalDateTime.now());
        r.setGeneResolution(res);
        return sauverEtRepondre(r);
    }

    /** Rouvre une gêne traitée (réservé MEDICAL/SUPER_ADMIN) : elle redevient active. */
    public RpeResponse rouvrirGene(UUID rpeId) {
        RpeSeance r = chargerAvecGene(rpeId, "Aucune gêne à rouvrir");
        r.setGeneTraitee(false);
        r.setGeneTraiteePar(null);
        r.setGeneTraiteeLe(null);
        r.setGeneResolution(null);
        return sauverEtRepondre(r);
    }

    private RpeSeance chargerAvecGene(UUID rpeId, String messageSiAbsente) {
        RpeSeance r = repository.findById(rpeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saisie introuvable"));
        scopeResolver.verifieAcces(r.getEquipeId());
        if (r.getGeneZone() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, messageSiAbsente);
        }
        return r;
    }

    private RpeResponse sauverEtRepondre(RpeSeance r) {
        Joueur joueur = joueurRepository.findById(r.getJoueurId()).orElse(null);
        Seance s = seanceRepository.findById(r.getSeanceId()).orElse(null);
        return toResponse(repository.save(r), joueur, s);
    }

    /** Séances des lignes données, en UNE requête (le titre et la durée prévue viennent de là). */
    private Map<UUID, Seance> seancesDe(List<RpeSeance> rows) {
        if (rows.isEmpty()) return Map.of();
        List<UUID> ids = rows.stream().map(RpeSeance::getSeanceId).distinct().toList();
        return seanceRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Seance::getId, Function.identity()));
    }

    private String videEnNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private RpeResponse toResponse(RpeSeance r, Joueur j, Seance s) {
        String titre = s == null ? null
                : (s.getTitre() != null && !s.getTitre().isBlank() ? s.getTitre()
                        : (s.getTypeSeance() != null ? s.getTypeSeance().getLibelle() : null));
        return new RpeResponse(
                r.getId(), r.getJoueurId(),
                j != null ? j.getNom() : null,
                j != null ? j.getPrenom() : null,
                r.getSeanceId(), r.getSeanceType(), r.getDate(),
                r.getRpe(), r.getDureeMinutes(), r.getCharge(),
                r.getPlaisir(), r.getCommentaire(),
                titre, s != null ? s.getDureeMinutes() : null,
                r.getGeneZone(), r.getGeneIntensite(), r.getGeneMoment(),
                r.isGeneTraitee(), r.getGeneResolution(), r.getGeneTraiteeLe(),
                r.getCreatedAt());
    }
}
