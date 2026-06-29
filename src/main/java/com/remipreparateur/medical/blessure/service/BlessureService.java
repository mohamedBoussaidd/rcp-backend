package com.remipreparateur.medical.blessure.service;

import com.remipreparateur.medical.blessure.dto.BlessureDtos.BlessureRequest;
import com.remipreparateur.medical.blessure.dto.BlessureDtos.BlessureResponse;
import com.remipreparateur.medical.blessure.entity.Blessure;
import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.medical.blessure.repository.BlessureRepository;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.shared.security.Scope;
import com.remipreparateur.shared.security.ScopeResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BlessureService {

    private final BlessureRepository blessureRepository;
    private final JoueurRepository joueurRepository;
    private final ScopeResolver scopeResolver;

    public BlessureService(BlessureRepository blessureRepository, JoueurRepository joueurRepository,
                           ScopeResolver scopeResolver) {
        this.blessureRepository = blessureRepository;
        this.joueurRepository = joueurRepository;
        this.scopeResolver = scopeResolver;
    }

    public List<BlessureResponse> lister(UUID joueurId) {
        List<Blessure> blessures;
        if (joueurId != null) {
            blessures = blessureRepository.findByJoueurIdOrderByDateBlessureDesc(joueurId);
        } else {
            Scope s = scopeResolver.resolve();
            if (s.all()) blessures = blessureRepository.findAllByOrderByDateBlessureDesc();
            else if (s.none()) blessures = List.of();
            else blessures = blessureRepository.findByEquipeIdInOrderByDateBlessureDesc(s.equipeIds());
        }

        Map<UUID, Joueur> joueurs = joueurRepository.findAllById(
                        blessures.stream().map(Blessure::getJoueurId).distinct().toList())
                .stream().collect(Collectors.toMap(Joueur::getId, Function.identity()));

        return blessures.stream().map(b -> toResponse(b, joueurs.get(b.getJoueurId()))).toList();
    }

    public BlessureResponse creer(BlessureRequest req) {
        Joueur joueur = joueurRepository.findById(req.joueurId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Joueur introuvable"));
        Blessure b = new Blessure();
        appliquer(b, req);
        b.setEquipeId(joueur.getEquipeId()); // rattache la blessure a l'equipe du joueur
        BlessureResponse res = toResponse(blessureRepository.save(b), joueur);
        synchroniserStatutJoueur(joueur);
        return res;
    }

    public BlessureResponse modifier(UUID id, BlessureRequest req) {
        Blessure b = blessureRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blessure introuvable"));
        scopeResolver.verifieAcces(b.getEquipeId());
        appliquer(b, req);
        Joueur joueur = joueurRepository.findById(b.getJoueurId()).orElse(null);
        BlessureResponse res = toResponse(blessureRepository.save(b), joueur);
        synchroniserStatutJoueur(joueur);
        return res;
    }

    public void supprimer(UUID id) {
        Blessure b = blessureRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blessure introuvable"));
        scopeResolver.verifieAcces(b.getEquipeId());
        UUID joueurId = b.getJoueurId();
        blessureRepository.deleteById(id);
        joueurRepository.findById(joueurId).ifPresent(this::synchroniserStatutJoueur);
    }

    private void appliquer(Blessure b, BlessureRequest req) {
        b.setJoueurId(req.joueurId());
        b.setDateBlessure(req.dateBlessure());
        b.setDateRetourEffectif(req.dateRetourEffectif());
        b.setDateRetourPrevue(req.dateRetourPrevue());
        String statut = (req.statut() == null || req.statut().isBlank()) ? "INDISPONIBLE" : req.statut().trim();
        b.setStatut(statut);
        // Retour acté sans date réelle saisie : on la fixe à aujourd'hui (pour le calcul des jours perdus).
        if ("RETABLI".equals(statut) && b.getDateRetourEffectif() == null) {
            b.setDateRetourEffectif(LocalDate.now());
        }
        b.setTypeBlessure(req.typeBlessure());
        b.setZoneCorporelle(req.zoneCorporelle());
        b.setCote(req.cote());
        b.setGravite(req.gravite());
        b.setCauseProbable(req.causeProbable());
        b.setRecidive(Boolean.TRUE.equals(req.recidive()));
        b.setCommentaire(req.commentaire());
        b.setNotesMedicales(req.notesMedicales());
        b.setRetourConfirme(true);   // toute saisie/édition manuelle vaut confirmation
    }

    /**
     * Réconciliation quotidienne : solde AUTOMATIQUEMENT les blessures dont la date de
     * retour prévue est dépassée (statut → RETABLI, date de retour effectif = date prévue),
     * remet le joueur disponible, mais marque {@code retourConfirme = false} pour que le
     * staff confirme ou prolonge. Renvoie les blessures soldées (pour notification).
     */
    public List<Blessure> cloturerRetoursDepasses() {
        List<Blessure> depasses = blessureRepository.findRetoursDepasses(LocalDate.now());
        for (Blessure b : depasses) {
            b.setStatut("RETABLI");
            if (b.getDateRetourEffectif() == null) {
                b.setDateRetourEffectif(b.getDateRetourPrevue());
            }
            b.setRetourConfirme(false);
            blessureRepository.save(b);
            joueurRepository.findById(b.getJoueurId()).ifPresent(this::synchroniserStatutJoueur);
        }
        return depasses;
    }

    /** Confirmation par le staff d'un retour soldé automatiquement. */
    public BlessureResponse confirmerRetour(UUID id) {
        Blessure b = blessureRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blessure introuvable"));
        scopeResolver.verifieAcces(b.getEquipeId());
        b.setRetourConfirme(true);
        Joueur joueur = joueurRepository.findById(b.getJoueurId()).orElse(null);
        return toResponse(blessureRepository.save(b), joueur);
    }

    /**
     * Bascule le statut joueur actif &lt;-&gt; blesse selon ses blessures actives.
     * Ne touche jamais aux statuts manuels (suspendu / prete / inactif).
     */
    private void synchroniserStatutJoueur(Joueur joueur) {
        if (joueur == null) return;
        boolean aBlessureActive = blessureRepository.findByJoueurIdOrderByDateBlessureDesc(joueur.getId())
                .stream().anyMatch(b -> !"RETABLI".equals(b.getStatut()));
        String statut = joueur.getStatut();
        if (aBlessureActive && "actif".equals(statut)) {
            joueur.setStatut("blesse");
            joueurRepository.save(joueur);
        } else if (!aBlessureActive && "blesse".equals(statut)) {
            joueur.setStatut("actif");
            joueurRepository.save(joueur);
        }
    }

    private BlessureResponse toResponse(Blessure b, Joueur j) {
        boolean enCours = !"RETABLI".equals(b.getStatut());
        return new BlessureResponse(
                b.getId(), b.getJoueurId(),
                j != null ? j.getNom() : null,
                j != null ? j.getPrenom() : null,
                b.getDateBlessure(), b.getDateRetourEffectif(), b.getDateRetourPrevue(), b.getStatut(),
                b.getTypeBlessure(), b.getZoneCorporelle(), b.getCote(),
                b.getGravite(), b.getCauseProbable(), b.isRecidive(),
                b.getCommentaire(), b.getNotesMedicales(), enCours, b.isRetourConfirme());
    }
}
