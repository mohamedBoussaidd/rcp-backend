package com.remipreparateur.medical.protocole.service;

import com.remipreparateur.medical.blessure.entity.Blessure;
import com.remipreparateur.medical.blessure.repository.BlessureRepository;
import com.remipreparateur.medical.protocole.dto.ProtocoleModeleDtos.DepuisBlessureRequest;
import com.remipreparateur.medical.protocole.dto.ProtocoleModeleDtos.EtapeModeleRequest;
import com.remipreparateur.medical.protocole.dto.ProtocoleModeleDtos.EtapeModeleResponse;
import com.remipreparateur.medical.protocole.dto.ProtocoleModeleDtos.ModeleRequest;
import com.remipreparateur.medical.protocole.dto.ProtocoleModeleDtos.ModeleResponse;
import com.remipreparateur.medical.protocole.entity.ProtocoleModele;
import com.remipreparateur.medical.protocole.entity.ProtocoleModeleEtape;
import com.remipreparateur.medical.protocole.repository.ProtocoleModeleEtapeRepository;
import com.remipreparateur.medical.protocole.repository.ProtocoleModeleRepository;
import com.remipreparateur.medical.rtp.entity.RtpEtape;
import com.remipreparateur.medical.rtp.repository.RtpEtapeRepository;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.shared.security.ScopeResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Bibliothèque des protocoles de reprise du club (modèles + critères de suggestion).
 * Accès : lecture blessures:read, écriture blessures:write (cf. SecurityConfig sur
 * /api/protocoles-modeles/**), scope club via {@link ScopeResolver#clubActif()}.
 */
@Service
public class ProtocoleModeleService {

    private final ProtocoleModeleRepository modeleRepository;
    private final ProtocoleModeleEtapeRepository etapeRepository;
    private final BlessureRepository blessureRepository;
    private final RtpEtapeRepository rtpEtapeRepository;
    private final JoueurRepository joueurRepository;
    private final ScopeResolver scopeResolver;

    public ProtocoleModeleService(ProtocoleModeleRepository modeleRepository,
                                  ProtocoleModeleEtapeRepository etapeRepository,
                                  BlessureRepository blessureRepository,
                                  RtpEtapeRepository rtpEtapeRepository,
                                  JoueurRepository joueurRepository,
                                  ScopeResolver scopeResolver) {
        this.modeleRepository = modeleRepository;
        this.etapeRepository = etapeRepository;
        this.blessureRepository = blessureRepository;
        this.rtpEtapeRepository = rtpEtapeRepository;
        this.joueurRepository = joueurRepository;
        this.scopeResolver = scopeResolver;
    }

    // ──────────────────────────── CRUD ────────────────────────────

    public List<ModeleResponse> lister() {
        UUID clubId = scopeResolver.clubActif();
        return modeleRepository.findByClubIdOrderByOrdreAscNomAsc(clubId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional
    public ModeleResponse creer(ModeleRequest req) {
        UUID clubId = scopeResolver.clubActif();
        ProtocoleModele m = new ProtocoleModele();
        m.setClubId(clubId);
        m.setOrdre(prochainOrdre(clubId));
        appliquer(m, req);
        modeleRepository.save(m);
        remplacerEtapes(m.getId(), req.etapes());
        return toResponse(m);
    }

    @Transactional
    public ModeleResponse modifier(UUID id, ModeleRequest req) {
        ProtocoleModele m = modeleChecke(id);
        appliquer(m, req);
        modeleRepository.save(m);
        remplacerEtapes(m.getId(), req.etapes());
        return toResponse(m);
    }

    @Transactional
    public void supprimer(UUID id) {
        ProtocoleModele m = modeleChecke(id);
        etapeRepository.deleteByModeleId(m.getId());
        modeleRepository.delete(m);
    }

    /** Copie indépendante (nom suffixé « (copie) »), pour créer une variante sans toucher l'original. */
    @Transactional
    public ModeleResponse dupliquer(UUID id) {
        ProtocoleModele source = modeleChecke(id);
        ProtocoleModele copie = new ProtocoleModele();
        copie.setClubId(source.getClubId());
        copie.setNom(tronquer(source.getNom() + " (copie)", 160));
        copie.setDescription(source.getDescription());
        copie.setActif(source.isActif());
        copie.setOrdre(prochainOrdre(source.getClubId()));
        copie.setTypesBlessure(source.getTypesBlessure());
        copie.setZonesCorporelles(source.getZonesCorporelles());
        copie.setGravites(source.getGravites());
        modeleRepository.save(copie);
        for (ProtocoleModeleEtape e : etapeRepository.findByModeleIdOrderByOrdreAsc(source.getId())) {
            ProtocoleModeleEtape ce = new ProtocoleModeleEtape();
            ce.setModeleId(copie.getId());
            ce.setOrdre(e.getOrdre());
            ce.setLibelle(e.getLibelle());
            ce.setJDebut(e.getJDebut());
            ce.setJFin(e.getJFin());
            ce.setDescription(e.getDescription());
            etapeRepository.save(ce);
        }
        return toResponse(copie);
    }

    /**
     * Capitalise le protocole en cours d'une blessure en nouveau modèle. Les critères de
     * suggestion sont pré-remplis avec le type/zone/gravité de la blessure d'origine.
     */
    @Transactional
    public ModeleResponse enregistrerDepuisBlessure(UUID blessureId, DepuisBlessureRequest req) {
        Blessure b = blessureRepository.findById(blessureId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blessure introuvable"));
        scopeResolver.verifieAcces(b.getEquipeId());
        List<RtpEtape> etapes = rtpEtapeRepository.findByBlessureIdOrderByOrdreAsc(blessureId);
        if (etapes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Aucun protocole en cours sur cette blessure");
        }
        UUID clubId = joueurRepository.findById(b.getJoueurId())
                .map(j -> j.getClubId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Fiche joueur introuvable"));

        ProtocoleModele m = new ProtocoleModele();
        m.setClubId(clubId);
        m.setNom(req.nom().trim());
        m.setDescription(videEnNull(req.description()));
        m.setActif(true);
        m.setOrdre(prochainOrdre(clubId));
        m.setTypesBlessure(videEnNull(b.getTypeBlessure()));
        m.setZonesCorporelles(videEnNull(b.getZoneCorporelle()));
        m.setGravites(videEnNull(b.getGravite()));
        modeleRepository.save(m);

        short ordre = 1;
        for (RtpEtape e : etapes) {
            ProtocoleModeleEtape me = new ProtocoleModeleEtape();
            me.setModeleId(m.getId());
            me.setOrdre(ordre++);
            me.setLibelle(e.getLibelle());
            me.setJDebut(e.getJDebut());
            me.setJFin(e.getJFin());
            me.setDescription(e.getDescription());
            etapeRepository.save(me);
        }
        return toResponse(m);
    }

    // ──────────────────────────── Suggestion ────────────────────────────

    /**
     * Meilleur modèle actif du club pour une blessure : chaque critère non nul doit contenir la
     * valeur de la blessure (sinon le modèle est écarté) ; le score = nombre de critères remplis
     * (spécificité). Un modèle sans critère (ex. « Protocole standard ») reste toujours éligible
     * à score 0. Renvoie null si aucun modèle actif n'est éligible.
     */
    public ProtocoleModele suggerer(UUID clubId, String type, String zone, String gravite) {
        ProtocoleModele meilleur = null;
        int meilleurScore = -1;
        for (ProtocoleModele m : modeleRepository.findByClubIdAndActifTrueOrderByOrdreAscNomAsc(clubId)) {
            int score = score(m, type, zone, gravite);
            if (score > meilleurScore) {
                meilleur = m;
                meilleurScore = score;
            }
        }
        return meilleur;
    }

    private int score(ProtocoleModele m, String type, String zone, String gravite) {
        int score = 0;
        Integer s;
        if ((s = scoreCritere(m.getTypesBlessure(), type)) == null) return -1;
        score += s;
        if ((s = scoreCritere(m.getZonesCorporelles(), zone)) == null) return -1;
        score += s;
        if ((s = scoreCritere(m.getGravites(), gravite)) == null) return -1;
        return score + s;
    }

    /** null = critère non satisfait (modèle écarté) ; 0 = critère absent ; 1 = critère rempli. */
    private Integer scoreCritere(String csv, String valeur) {
        List<String> codes = deserialiser(csv);
        if (codes.isEmpty()) return 0;
        if (valeur == null || valeur.isBlank()) return null;
        return codes.contains(valeur.trim().toLowerCase()) ? 1 : null;
    }

    // ──────────────────────────── Helpers ────────────────────────────

    public ModeleResponse toResponse(ProtocoleModele m) {
        List<EtapeModeleResponse> etapes = etapeRepository.findByModeleIdOrderByOrdreAsc(m.getId()).stream()
                .map(e -> new EtapeModeleResponse(e.getId(), e.getOrdre(), e.getLibelle(),
                        e.getJDebut(), e.getJFin(), e.getDescription()))
                .toList();
        return new ModeleResponse(m.getId(), m.getNom(), m.getDescription(), m.isActif(), m.getOrdre(),
                deserialiser(m.getTypesBlessure()), deserialiser(m.getZonesCorporelles()),
                deserialiser(m.getGravites()), etapes, m.getCreatedAt());
    }

    /** Étapes types d'un modèle, dans l'ordre (pour le clonage à l'initialisation d'un RTP). */
    public List<ProtocoleModeleEtape> etapesDe(UUID modeleId) {
        return etapeRepository.findByModeleIdOrderByOrdreAsc(modeleId);
    }

    /** Modèle du club vérifié (404 hors club/inexistant). */
    public ProtocoleModele modeleChecke(UUID id) {
        ProtocoleModele m = modeleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Modèle introuvable"));
        scopeResolver.verifieAccesClub(m.getClubId());
        return m;
    }

    private void appliquer(ProtocoleModele m, ModeleRequest req) {
        m.setNom(req.nom().trim());
        m.setDescription(videEnNull(req.description()));
        m.setActif(req.actif() == null || req.actif());
        m.setTypesBlessure(serialiser(req.typesBlessure()));
        m.setZonesCorporelles(serialiser(req.zonesCorporelles()));
        m.setGravites(serialiser(req.gravites()));
    }

    private void remplacerEtapes(UUID modeleId, List<EtapeModeleRequest> etapes) {
        etapeRepository.deleteByModeleId(modeleId);
        short ordre = 1;
        for (EtapeModeleRequest req : etapes) {
            ProtocoleModeleEtape e = new ProtocoleModeleEtape();
            e.setModeleId(modeleId);
            e.setOrdre(ordre++);
            e.setLibelle(req.libelle().trim());
            e.setJDebut(req.jDebut());
            e.setJFin(req.jFin());
            e.setDescription(videEnNull(req.description()));
            etapeRepository.save(e);
        }
    }

    private short prochainOrdre(UUID clubId) {
        return (short) (modeleRepository.findByClubIdOrderByOrdreAscNomAsc(clubId).stream()
                .mapToInt(ProtocoleModele::getOrdre).max().orElse(0) + 1);
    }

    private String serialiser(List<String> codes) {
        if (codes == null) return null;
        String csv = codes.stream()
                .filter(c -> c != null && !c.isBlank())
                .map(c -> c.trim().toLowerCase())
                .distinct()
                .collect(Collectors.joining(","));
        return csv.isEmpty() ? null : csv;
    }

    private List<String> deserialiser(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private String videEnNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private String tronquer(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
