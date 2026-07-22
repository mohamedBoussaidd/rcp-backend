package com.remipreparateur.notification.service;

import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.notification.dto.NotifConfigDtos.ConfigDto;
import com.remipreparateur.notification.dto.NotifConfigDtos.DroitEnvoiDto;
import com.remipreparateur.notification.dto.NotifConfigDtos.RoutageDto;
import com.remipreparateur.notification.entity.NiveauEnvoi;
import com.remipreparateur.notification.entity.NotifConfigEquipe;
import com.remipreparateur.notification.entity.NotifDroitEnvoi;
import com.remipreparateur.notification.entity.NotifRoutage;
import com.remipreparateur.notification.entity.TypeNotification;
import com.remipreparateur.notification.repository.NotifConfigEquipeRepository;
import com.remipreparateur.notification.repository.NotifDroitEnvoiRepository;
import com.remipreparateur.notification.repository.NotifRoutageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Accès à la configuration de notification d'une équipe : config de seuils/digests/rappels
 * (créée à la demande avec les défauts) et routage par type (semé avec un mapping rôle par
 * défaut, ensuite réajustable par le staff). Partagé par les producteurs (P3) et l'API config (P4).
 */
@Service
public class NotifConfigService {

    /** Routage par défaut : quel(s) rôle(s) reçoivent chaque type destiné au staff. */
    private static final Map<TypeNotification, String> ROUTAGE_DEFAUT = new EnumMap<>(TypeNotification.class);
    static {
        ROUTAGE_DEFAUT.put(TypeNotification.ALERTE_CHARGE, "PREPARATEUR");
        ROUTAGE_DEFAUT.put(TypeNotification.ALERTE_READINESS, "PREPARATEUR");
        ROUTAGE_DEFAUT.put(TypeNotification.ALERTE_COMPLETION, "PREPARATEUR");
        ROUTAGE_DEFAUT.put(TypeNotification.ALERTE_POIDS, "PREPARATEUR");
        ROUTAGE_DEFAUT.put(TypeNotification.ALERTE_WELLNESS, "MEDICAL,PREPARATEUR");
        ROUTAGE_DEFAUT.put(TypeNotification.ALERTE_GENE, "MEDICAL,PREPARATEUR");
        ROUTAGE_DEFAUT.put(TypeNotification.ALERTE_STATUT, "ENTRAINEUR,PREPARATEUR,MEDICAL");
        ROUTAGE_DEFAUT.put(TypeNotification.DIGEST, "PREPARATEUR,MEDICAL");
        ROUTAGE_DEFAUT.put(TypeNotification.COMPTE, "PRESIDENT");
        ROUTAGE_DEFAUT.put(TypeNotification.ECHEANCE, "MEDICAL");
    }

    private final NotifConfigEquipeRepository configRepository;
    private final NotifRoutageRepository routageRepository;
    private final NotifDroitEnvoiRepository droitRepository;
    private final JoueurRepository joueurRepository;

    public NotifConfigService(NotifConfigEquipeRepository configRepository,
                              NotifRoutageRepository routageRepository,
                              NotifDroitEnvoiRepository droitRepository,
                              JoueurRepository joueurRepository) {
        this.configRepository = configRepository;
        this.routageRepository = routageRepository;
        this.droitRepository = droitRepository;
        this.joueurRepository = joueurRepository;
    }

    /** Config de l'équipe, créée avec les valeurs par défaut si absente. */
    @Transactional
    public NotifConfigEquipe getOrCreate(UUID equipeId) {
        return configRepository.findByEquipeId(equipeId).orElseGet(() -> {
            NotifConfigEquipe c = new NotifConfigEquipe();
            c.setEquipeId(equipeId);
            c.setUpdatedAt(LocalDateTime.now());
            return configRepository.save(c);
        });
    }

    /** Routages de l'équipe (semés depuis le mapping par défaut au premier accès). */
    @Transactional
    public List<NotifRoutage> getOrSeedRoutages(UUID equipeId) {
        List<NotifRoutage> existants = routageRepository.findByEquipeId(equipeId);
        if (!existants.isEmpty()) return existants;
        ROUTAGE_DEFAUT.forEach((type, roles) -> {
            NotifRoutage r = new NotifRoutage();
            r.setEquipeId(equipeId);
            r.setType(type);
            r.setRoles(roles);
            r.setActif(true);
            routageRepository.save(r);
        });
        return routageRepository.findByEquipeId(equipeId);
    }

    /** Rôles destinataires d'un type pour une équipe (vide si type inactif/non routé). */
    @Transactional
    public List<String> rolesPour(UUID equipeId, TypeNotification type) {
        getOrSeedRoutages(equipeId);
        return routageRepository.findByEquipeIdAndType(equipeId, type)
                .filter(NotifRoutage::isActif)
                .map(NotifRoutage::rolesList)
                .orElseGet(() -> {
                    String def = ROUTAGE_DEFAUT.get(type);
                    return def == null ? List.of() : List.of(def.split(","));
                });
    }

    // ──────────────────────────── Config (lecture/écriture) ────────────────────────────

    @Transactional
    public ConfigDto getConfigDto(UUID equipeId) {
        return toDto(getOrCreate(equipeId));
    }

    @Transactional
    public ConfigDto updateConfig(UUID equipeId, ConfigDto d) {
        NotifConfigEquipe c = getOrCreate(equipeId);
        c.setSeuilAcwrHaut(d.seuilAcwrHaut());
        c.setSeuilAcwrBas(d.seuilAcwrBas());
        c.setSeuilReadinessMin(d.seuilReadinessMin());
        c.setSeuilWellnessFatigue(d.seuilWellnessFatigue());
        c.setSeuilWellnessDouleur(d.seuilWellnessDouleur());
        c.setSeuilWellnessStress(d.seuilWellnessStress());
        c.setSeuilWellnessSommeil(d.seuilWellnessSommeil());
        c.setSeuilWellnessHumeur(d.seuilWellnessHumeur());
        c.setSeuilPoidsCourt(d.seuilPoidsCourt());
        c.setSeuilPoidsMoyen(d.seuilPoidsMoyen());
        c.setSeuilCompletionMin(d.seuilCompletionMin());
        c.setDigestActif(d.digestActif());
        c.setDigestMatinHeure(d.digestMatinHeure());
        c.setDigestSoirHeure(d.digestSoirHeure());
        c.setDigestJours(normaliserJours(d.digestJours(), "1,2,3,4,5,6,7"));
        c.setDigestPoidsJours(normaliserJours(d.digestPoidsJours(), ""));
        c.setRappelWellnessActif(d.rappelWellnessActif());
        c.setRappelWellnessHeure(d.rappelWellnessHeure());
        c.setRappelWellnessJours(normaliserJours(d.rappelWellnessJours(), "1,2,3,4,5,6,7"));
        c.setRappelRpeActif(d.rappelRpeActif());
        c.setRappelRpeDelaiHeures(d.rappelRpeDelaiHeures());
        c.setRappelSeanceActif(d.rappelSeanceActif());
        c.setUpdatedAt(LocalDateTime.now());
        return toDto(configRepository.save(c));
    }

    private ConfigDto toDto(NotifConfigEquipe c) {
        return new ConfigDto(
                c.getSeuilAcwrHaut(), c.getSeuilAcwrBas(), c.getSeuilReadinessMin(),
                c.getSeuilWellnessFatigue(), c.getSeuilWellnessDouleur(), c.getSeuilWellnessStress(),
                c.getSeuilWellnessSommeil(), c.getSeuilWellnessHumeur(),
                c.getSeuilPoidsCourt(), c.getSeuilPoidsMoyen(), c.getSeuilCompletionMin(),
                c.isDigestActif(), c.getDigestMatinHeure(), c.getDigestSoirHeure(),
                c.getDigestJours(), c.getDigestPoidsJours(),
                c.isRappelWellnessActif(), c.getRappelWellnessHeure(), c.getRappelWellnessJours(),
                c.isRappelRpeActif(), c.getRappelRpeDelaiHeures(),
                c.isRappelSeanceActif());
    }

    /**
     * Nettoie une saisie de jours (CSV ISO 1..7) : ne garde que 1..7, dédoublonne et trie.
     * Une saisie vide reste vide (= jamais) sauf si {@code defautSiVide} est fourni.
     */
    private static String normaliserJours(String csv, String defautSiVide) {
        java.util.List<Integer> jours = com.remipreparateur.notification.service.CadenceJours.parse(csv)
                .stream().sorted().toList();
        if (jours.isEmpty()) return defautSiVide;
        return jours.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }

    // ──────────────────────────── Routage (lecture/écriture) ────────────────────────────

    @Transactional
    public List<RoutageDto> listRoutages(UUID equipeId) {
        return getOrSeedRoutages(equipeId).stream()
                .map(r -> new RoutageDto(r.getType(), r.getRoles(), r.isActif()))
                .toList();
    }

    @Transactional
    public List<RoutageDto> updateRoutages(UUID equipeId, List<RoutageDto> dtos) {
        getOrSeedRoutages(equipeId);
        for (RoutageDto d : dtos) {
            NotifRoutage r = routageRepository.findByEquipeIdAndType(equipeId, d.type())
                    .orElseGet(() -> {
                        NotifRoutage nr = new NotifRoutage();
                        nr.setEquipeId(equipeId);
                        nr.setType(d.type());
                        return nr;
                    });
            r.setRoles(d.roles() == null ? "" : d.roles().trim());
            r.setActif(d.actif());
            routageRepository.save(r);
        }
        return listRoutages(equipeId);
    }

    // ──────────────────────────── Droits d'envoi joueur ────────────────────────────

    /** Tous les joueurs de l'équipe avec leur niveau d'émission (AUCUN par défaut). */
    @Transactional(readOnly = true)
    public List<DroitEnvoiDto> listDroits(UUID equipeId) {
        Map<UUID, NiveauEnvoi> niveaux = new java.util.HashMap<>();
        droitRepository.findByEquipeId(equipeId).forEach(d -> niveaux.put(d.getJoueurId(), d.getNiveau()));
        return joueurRepository.findByEquipeIdIn(List.of(equipeId)).stream()
                .filter(j -> !"inactif".equalsIgnoreCase(j.getStatut()))
                .map(j -> new DroitEnvoiDto(j.getId(), (j.getPrenom() + " " + j.getNom()).trim(),
                        niveaux.getOrDefault(j.getId(), NiveauEnvoi.AUCUN)))
                .toList();
    }

    @Transactional
    public DroitEnvoiDto setDroit(UUID equipeId, UUID joueurId, NiveauEnvoi niveau) {
        Joueur j = joueurRepository.findById(joueurId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Joueur introuvable"));
        NotifDroitEnvoi d = droitRepository.findByJoueurId(joueurId).orElseGet(NotifDroitEnvoi::new);
        d.setJoueurId(joueurId);
        d.setEquipeId(equipeId);
        d.setNiveau(niveau == null ? NiveauEnvoi.AUCUN : niveau);
        droitRepository.save(d);
        return new DroitEnvoiDto(joueurId, (j.getPrenom() + " " + j.getNom()).trim(), d.getNiveau());
    }

    /** Niveau d'émission d'un joueur (AUCUN si non défini). */
    @Transactional(readOnly = true)
    public NiveauEnvoi niveauEnvoi(UUID joueurId) {
        return droitRepository.findByJoueurId(joueurId).map(NotifDroitEnvoi::getNiveau).orElse(NiveauEnvoi.AUCUN);
    }
}
