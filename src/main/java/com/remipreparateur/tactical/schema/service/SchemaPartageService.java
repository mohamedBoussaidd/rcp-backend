package com.remipreparateur.tactical.schema.service;

import com.remipreparateur.auth.entity.Role;
import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.auth.rbac.FeatureModule;
import com.remipreparateur.auth.rbac.PermissionResolver;
import com.remipreparateur.auth.repository.UtilisateurRepository;
import com.remipreparateur.club.pack.ClubModulesService;
import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.notification.entity.Priorite;
import com.remipreparateur.notification.entity.TypeNotification;
import com.remipreparateur.notification.service.NotificationDispatcher;
import com.remipreparateur.saison.service.AppartenanceService;
import com.remipreparateur.shared.security.CurrentUserProvider;
import com.remipreparateur.shared.security.ScopeResolver;
import com.remipreparateur.tactical.schema.dto.SchemaPartageDtos.MonSchemaResponse;
import com.remipreparateur.tactical.schema.dto.SchemaPartageDtos.PartageRequest;
import com.remipreparateur.tactical.schema.dto.SchemaPartageDtos.PartageResponse;
import com.remipreparateur.tactical.schema.entity.SchemaPartage;
import com.remipreparateur.tactical.schema.entity.SchemaTactique;
import com.remipreparateur.tactical.schema.repository.SchemaPartageRepository;
import com.remipreparateur.tactical.schema.repository.SchemaTactiqueRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Partage de schémas tactiques aux joueurs (V100).
 *
 * <p>Deux portées possibles, combinables : l'ÉQUIPE active et/ou des JOUEURS nommés. Chaque
 * destinataire reçoit une notification in-app (et Web Push si son abonnement existe) pointant
 * sur son espace — sans quoi personne ne verrait jamais le schéma.
 *
 * <p>Le partage <b>référence</b> le schéma, il ne le recopie pas : corriger le schéma corrige ce
 * que voient les joueurs. C'est l'inverse d'une diapo (instantané figé), et c'est délibéré.
 */
@Service
public class SchemaPartageService {

    /** Lien ouvert par la notification : l'écran « Schémas » de l'application joueur. */
    private static final String LIEN_JOUEUR = "/joueur/schemas";

    private final SchemaPartageRepository partageRepository;
    private final SchemaTactiqueRepository schemaRepository;
    private final JoueurRepository joueurRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final NotificationDispatcher dispatcher;
    private final AppartenanceService appartenance;
    private final ScopeResolver scopeResolver;
    private final PermissionResolver permissionResolver;
    private final ClubModulesService clubModulesService;
    private final CurrentUserProvider currentUser;

    public SchemaPartageService(SchemaPartageRepository partageRepository,
                                SchemaTactiqueRepository schemaRepository,
                                JoueurRepository joueurRepository,
                                UtilisateurRepository utilisateurRepository,
                                NotificationDispatcher dispatcher,
                                AppartenanceService appartenance,
                                ScopeResolver scopeResolver,
                                PermissionResolver permissionResolver,
                                ClubModulesService clubModulesService,
                                CurrentUserProvider currentUser) {
        this.partageRepository = partageRepository;
        this.schemaRepository = schemaRepository;
        this.joueurRepository = joueurRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.dispatcher = dispatcher;
        this.appartenance = appartenance;
        this.scopeResolver = scopeResolver;
        this.permissionResolver = permissionResolver;
        this.clubModulesService = clubModulesService;
        this.currentUser = currentUser;
    }

    // ──────────────────────────── Côté staff ────────────────────────────

    /**
     * Partage un schéma et notifie les destinataires. Renvoie les lignes créées (une par cible).
     *
     * <p>Un schéma FOURNI (global, sans club) est partageable tel quel : le club ne le possède
     * pas, mais il a le droit de le montrer à ses joueurs.
     */
    @Transactional
    public List<PartageResponse> partager(PartageRequest req) {
        exigeModule();
        Utilisateur u = currentUser.current();
        UUID clubId = clubActif();
        SchemaTactique schema = schemaRepository.findById(req.schemaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schéma introuvable"));
        if (schema.getClubId() != null && !schema.getClubId().equals(clubId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Schéma introuvable");
        }

        List<UUID> joueurs = req.joueurIds() == null ? List.of()
                : req.joueurIds().stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (!req.equipe() && joueurs.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucun destinataire");
        }

        UUID equipeId = scopeResolver.equipeActiveUnique();
        String titre = titreOu(req.titre(), schema.getNom());
        String corps = req.message() != null && !req.message().isBlank()
                ? req.message().trim()
                : "Un schéma a été partagé par le staff.";

        List<SchemaPartage> crees = new ArrayList<>();
        if (req.equipe()) {
            crees.add(enregistrer(clubId, schema.getId(), equipeId, null, titre, req.message(), u.getId()));
            dispatcher.versEquipeJoueurs(equipeId, TypeNotification.SCHEMA_PARTAGE, titre, corps,
                    LIEN_JOUEUR, u.getId(), UUID.randomUUID(), false);
        }
        for (UUID joueurId : joueurs) {
            Joueur j = joueurRepository.findById(joueurId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Joueur introuvable"));
            // Anti-IDOR : on ne partage qu'à un joueur de l'équipe active.
            if (equipeId == null || !appartenance.equipesDe(j.getId()).contains(equipeId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Destinataire hors équipe");
            }
            crees.add(enregistrer(clubId, schema.getId(), null, joueurId, titre, req.message(), u.getId()));
            dispatcher.versJoueurFiche(equipeId, joueurId, TypeNotification.SCHEMA_PARTAGE, titre, corps,
                    LIEN_JOUEUR, Priorite.NORMALE, u.getId(), UUID.randomUUID(), false);
        }
        Map<UUID, String> noms = new HashMap<>();
        return crees.stream().map(p -> toResponse(p, schema, noms)).toList();
    }

    /** Historique des partages du club (le plus récent d'abord). */
    @Transactional(readOnly = true)
    public List<PartageResponse> lister(UUID schemaId) {
        exigeModule();
        UUID clubId = clubActif();
        List<SchemaPartage> lignes = schemaId != null
                ? partageRepository.findBySchemaIdOrderByCreatedAtDesc(schemaId)
                : partageRepository.findByClubIdOrderByCreatedAtDesc(clubId);
        Map<UUID, String> noms = new HashMap<>();
        Map<UUID, SchemaTactique> schemas = new HashMap<>();
        return lignes.stream()
                .filter(p -> clubId == null || clubId.equals(p.getClubId()))
                .map(p -> toResponse(p, schemas.computeIfAbsent(p.getSchemaId(),
                        id -> schemaRepository.findById(id).orElse(null)), noms))
                .toList();
    }

    /** Retire un partage : le schéma disparaît de l'espace des joueurs concernés. */
    @Transactional
    public void retirer(UUID id) {
        exigeModule();
        UUID clubId = clubActif();
        SchemaPartage p = partageRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partage introuvable"));
        if (clubId != null && !clubId.equals(p.getClubId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Partage introuvable");
        }
        partageRepository.delete(p);
    }

    // ──────────────────────────── Côté joueur ────────────────────────────

    /**
     * Les schémas reçus par un joueur : ceux qui le visent nommément et ceux adressés à l'une de
     * ses équipes. Un même schéma partagé deux fois n'apparaît qu'une fois — le joueur se moque
     * de savoir qu'il l'a reçu deux fois, et le partage nominatif l'emporte (il est plus précis).
     */
    @Transactional(readOnly = true)
    public List<MonSchemaResponse> mesSchemas(UUID joueurId) {
        exigeModule();
        Set<UUID> equipes = new LinkedHashSet<>(appartenance.equipesDe(joueurId));
        if (equipes.isEmpty()) equipes.add(new UUID(0, 0));   // IN () est invalide en JPQL
        List<SchemaPartage> lignes = partageRepository.pourJoueur(joueurId, equipes);
        Map<UUID, SchemaTactique> schemas = new HashMap<>();
        Set<UUID> vus = new LinkedHashSet<>();
        List<MonSchemaResponse> res = new ArrayList<>();
        for (SchemaPartage p : lignes) {
            if (!vus.add(p.getSchemaId())) continue;
            SchemaTactique s = schemas.computeIfAbsent(p.getSchemaId(),
                    id -> schemaRepository.findById(id).orElse(null));
            if (s == null) continue;
            res.add(new MonSchemaResponse(p.getId(), s.getId(), titreOu(p.getTitre(), s.getNom()),
                    p.getMessage(), s.getSchemaJson(), s.getApercu(),
                    p.getJoueurId() != null, p.getCreatedAt()));
        }
        return res;
    }

    // ──────────────────────────── Helpers ────────────────────────────

    private SchemaPartage enregistrer(UUID clubId, UUID schemaId, UUID equipeId, UUID joueurId,
                                      String titre, String message, UUID auteur) {
        SchemaPartage p = new SchemaPartage();
        p.setClubId(clubId);
        p.setSchemaId(schemaId);
        p.setEquipeId(equipeId);
        p.setJoueurId(joueurId);
        p.setTitre(titre);
        p.setMessage(message != null && !message.isBlank() ? message.trim() : null);
        p.setCreePar(auteur);
        return partageRepository.save(p);
    }

    private PartageResponse toResponse(SchemaPartage p, SchemaTactique schema, Map<UUID, String> noms) {
        String destinataire = p.getJoueurId() != null
                ? noms.computeIfAbsent(p.getJoueurId(), id -> joueurRepository.findById(id)
                    .map(j -> ((j.getPrenom() != null ? j.getPrenom() + " " : "") + (j.getNom() != null ? j.getNom() : "")).trim())
                    .orElse("Joueur"))
                : "Toute l'équipe";
        String parNom = p.getCreePar() == null ? null
                : noms.computeIfAbsent(p.getCreePar(), id -> utilisateurRepository.findById(id)
                    .map(c -> ((c.getPrenom() != null ? c.getPrenom() + " " : "") + (c.getNom() != null ? c.getNom() : "")).trim())
                    .orElse(null));
        return new PartageResponse(p.getId(), p.getSchemaId(), schema != null ? schema.getNom() : null,
                p.getEquipeId(), p.getJoueurId(), destinataire, p.getTitre(), p.getMessage(),
                parNom, p.getCreatedAt());
    }

    private static String titreOu(String titre, String defaut) {
        return titre != null && !titre.isBlank() ? titre.trim() : defaut;
    }

    private UUID clubActif() {
        UUID clubId = permissionResolver.clubActif(currentUser.current());
        if (clubId == null && currentUser.current().getRole() != Role.SUPER_ADMIN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Aucun club actif");
        }
        return clubId;
    }

    /** Double verrou : la permission est vérifiée par la sécurité, le module l'est ici. */
    private void exigeModule() {
        UUID clubId = permissionResolver.clubActif(currentUser.current());
        if (clubId == null) return;   // super-admin hors contexte : rien à verrouiller
        if (!clubModulesService.modulesActifs(clubId).contains(FeatureModule.SCHEMAS_JOUEUR.getCode())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Le module « " + FeatureModule.SCHEMAS_JOUEUR.getLibelle() + " » n'est pas activé pour votre club.");
        }
    }
}
