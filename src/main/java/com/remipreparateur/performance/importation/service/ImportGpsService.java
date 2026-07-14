package com.remipreparateur.performance.importation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remipreparateur.club.repository.EquipeRepository;
import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.joueur.service.JoueurService;
import com.remipreparateur.performance.gps.entity.DonneeGps;
import com.remipreparateur.performance.gps.repository.DonneeGpsRepository;
import com.remipreparateur.performance.importation.dto.*;
import com.remipreparateur.performance.importation.entity.AliasJoueurImport;
import com.remipreparateur.performance.importation.entity.ProfilImportGps;
import com.remipreparateur.performance.importation.repository.AliasJoueurImportRepository;
import com.remipreparateur.performance.importation.repository.ProfilImportGpsRepository;
import com.remipreparateur.performance.importation.service.LecteurFichierTabulaire.LigneBrute;
import com.remipreparateur.performance.importation.service.LecteurFichierTabulaire.Tableau;
import com.remipreparateur.performance.seance.entity.Seance;
import com.remipreparateur.performance.seance.repository.SeanceRepository;
import com.remipreparateur.shared.security.ScopeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.remipreparateur.performance.importation.service.ImportNormalisation.normalise;

/**
 * Import GPS flexible : analyse d'un fichier tabulaire quelconque via un profil de mapping
 * (reconnu par signature d'en-têtes, suggéré par dictionnaire sinon), conversion vers les
 * métriques internes (unités, durées hh:mm:ss, bandes de vitesse re-cumulées), vérifications
 * de vraisemblance NON bloquantes, matching joueur scopé club avec alias mémorisés.
 */
@Service
@RequiredArgsConstructor
public class ImportGpsService {

    private final LecteurFichierTabulaire lecteur;
    private final DictionnaireMetriques dictionnaire;
    private final ProfilImportGpsRepository profilRepository;
    private final AliasJoueurImportRepository aliasRepository;
    private final SeanceRepository seanceRepository;
    private final EquipeRepository equipeRepository;
    private final JoueurRepository joueurRepository;
    private final JoueurService joueurService;
    private final DonneeGpsRepository donneeGpsRepository;
    private final ScopeResolver scopeResolver;
    private final ObjectMapper objectMapper;

    /* ═════════════════════════ ANALYSE ═════════════════════════ */

    public AnalyseImportResponse analyser(byte[] contenu, String nomFichier, String texteColle,
                                          UUID seanceId, String mappingsJson, String formatIdentite,
                                          boolean enregistrerProfil, String nomProfil) {
        Seance seance = seanceRepository.findById(seanceId)
                .orElseThrow(() -> new IllegalArgumentException("Séance introuvable : " + seanceId));
        scopeResolver.verifieAcces(seance.getEquipeId());
        UUID clubId = clubDeSeance(seance);

        Tableau tableau = texteColle != null && !texteColle.isBlank()
                ? lecteur.lireTexteColle(texteColle)
                : lecteur.lire(contenu, nomFichier);
        String signature = ImportNormalisation.signature(tableau.entetes());

        AnalyseImportResponse reponse = new AnalyseImportResponse();
        reponse.setSeanceId(seanceId.toString());
        reponse.setSignatureEntetes(signature);
        reponse.setColonnes(detecteColonnes(tableau));

        // Mappings : fournis par le front (écran validé) > profil du club > profil global.
        List<MappingColonne> mappings = null;
        String formatRetenu = formatIdentite;
        if (mappingsJson != null && !mappingsJson.isBlank()) {
            mappings = parseMappings(mappingsJson);
        } else {
            Optional<ProfilImportGps> profil = profilRepository
                    .findFirstByClubIdAndSignatureEntetes(clubId, signature)
                    .or(() -> profilRepository.findFirstByClubIdIsNullAndSignatureEntetes(signature));
            if (profil.isPresent()) {
                mappings = parseMappings(profil.get().getMappings());
                formatRetenu = profil.get().getFormatIdentite();
                reponse.setProfilUtilise(versDto(profil.get()));
                if (profil.get().getClubId() == null) {
                    // Profil global (fournisseur) reconnu : cloné en profil du club pour que
                    // l'affichage niveau 1 (libellés/masquage) connaisse les métriques du club.
                    sauvegarderProfilClub(clubId, signature, mappings, formatRetenu, profil.get().getNom(), null);
                }
            }
        }

        if (mappings == null) {
            reponse.setStatut("MAPPING_REQUIS");
            reponse.setFormatIdentiteSuggere(suggereFormatIdentite(tableau, reponse.getColonnes()));
            List<ProfilImportDto> disponibles = new ArrayList<>();
            profilRepository.findByClubId(clubId).forEach(p -> disponibles.add(versDto(p)));
            profilRepository.findByClubIdIsNull().forEach(p -> disponibles.add(versDto(p)));
            reponse.setProfilsDisponibles(disponibles);
            return reponse;
        }

        if (formatRetenu == null || formatRetenu.isBlank()) formatRetenu = "PRENOM_NOM";
        reponse.setFormatIdentiteSuggere(formatRetenu);
        convertirEtVerifier(tableau, mappings, formatRetenu, seance, clubId, reponse);

        if (enregistrerProfil && mappingsJson != null) {
            sauvegarderProfilClub(clubId, signature, mappings, formatRetenu, nomProfil, nomFichier);
        }
        reponse.setStatut("PRET");
        return reponse;
    }

    /* ═════════════════════════ CONFIRMATION ═════════════════════════ */

    @Transactional
    public Map<String, Object> confirmer(ConfirmerImportRequest request) {
        UUID seanceId = UUID.fromString(request.getSeanceId());
        Seance seance = seanceRepository.findById(seanceId)
                .orElseThrow(() -> new IllegalArgumentException("Séance introuvable : " + seanceId));
        scopeResolver.verifieAcces(seance.getEquipeId());
        UUID clubId = clubDeSeance(seance);

        Map<String, UUID> resolutionMap = new HashMap<>();
        if (request.getResolutions() != null) {
            for (ResolutionImportDto res : request.getResolutions()) {
                switch (res.getAction()) {
                    case "IGNORE" -> { /* skip */ }
                    case "MERGE" -> {
                        UUID joueurId = UUID.fromString(res.getJoueurExistantId());
                        Joueur j = joueurRepository.findById(joueurId)
                                .orElseThrow(() -> new IllegalArgumentException("Joueur introuvable"));
                        if (!clubId.equals(j.getClubId())) {
                            throw new IllegalArgumentException("Joueur hors du club de la séance");
                        }
                        resolutionMap.put(res.getIdentiteFichier(), joueurId);
                        memoriseAlias(clubId, res.getIdentiteFichier(), joueurId);
                    }
                    case "CREATE" -> {
                        Joueur j = new Joueur();
                        j.setPrenom(res.getPrenom() != null ? res.getPrenom() : res.getIdentiteFichier());
                        j.setNom(res.getNom());
                        j.setPostePrincipal(res.getPoste());
                        j.setStatut("actif");
                        j.setClubId(clubId);
                        j = joueurRepository.save(j);
                        resolutionMap.put(res.getIdentiteFichier(), j.getId());
                        memoriseAlias(clubId, res.getIdentiteFichier(), j.getId());
                    }
                }
            }
        }

        int inseres = 0;
        int ignores = 0;
        for (LigneGpsImportDto ligne : request.getLignes()) {
            UUID joueurId;
            if (ligne.getJoueurId() != null) {
                joueurId = UUID.fromString(ligne.getJoueurId());
            } else if (resolutionMap.containsKey(ligne.getIdentiteFichier())) {
                joueurId = resolutionMap.get(ligne.getIdentiteFichier());
            } else {
                ignores++;
                continue;
            }

            Joueur joueur = joueurRepository.findById(joueurId).orElse(null);
            if (joueur == null || !clubId.equals(joueur.getClubId())) { ignores++; continue; }

            // Une fiche qui reçoit du GPS doit être dans l'effectif de la saison en cours, sinon
            // elle reste invisible partout (État de l'effectif, dashboards) : inscription sur
            // l'équipe de la séance, sauf si le joueur est déjà inscrit dans une autre équipe.
            joueurService.inscrireEffectifSiHorsSaison(joueur, seance.getEquipeId());

            final Joueur finalJoueur = joueur;
            DonneeGps gps = donneeGpsRepository
                    .findByJoueurIdAndSeanceId(joueurId, seance.getId())
                    .orElseGet(() -> {
                        DonneeGps g = new DonneeGps();
                        g.setJoueur(finalJoueur);
                        g.setSeance(seance);
                        return g;
                    });

            gps.setDureeMinutes(ligne.getDureeMinutes());
            gps.setDistanceTotaleM(ligne.getDistanceTotaleM());
            gps.setDistance15kmhM(ligne.getDistance15kmhM());
            gps.setDistance19kmhM(ligne.getDistance19kmhM());
            gps.setDistanceSprint24kmhM(ligne.getDistanceSprint24kmhM());
            gps.setDistanceSprint28kmhM(ligne.getDistanceSprint28kmhM());
            gps.setNbSprints24kmh(ligne.getNbSprints24kmh());
            gps.setVitesseMaxKmh(ligne.getVitesseMaxKmh());
            gps.setNbAccelerations(ligne.getNbAccelerations());
            gps.setNbFreinages(ligne.getNbFreinages());
            gps.setRatioDistanceMin(ligne.getRatioDistanceMin());
            donneeGpsRepository.save(gps);
            inseres++;
        }

        // Marquer la séance comme réalisée (uniquement si elle a eu lieu : jour J ou passé).
        if (!"REALISEE".equals(seance.getStatut())
                && seance.getDate() != null && !seance.getDate().isAfter(LocalDate.now())) {
            seance.setStatut("REALISEE");
            seanceRepository.save(seance);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("seanceId", seanceId.toString());
        result.put("inseres", inseres);
        result.put("ignores", ignores);
        return result;
    }

    /* ═════════════════════════ PROFILS ═════════════════════════ */

    public List<ProfilImportDto> listerProfils(UUID seanceId) {
        Seance seance = seanceRepository.findById(seanceId)
                .orElseThrow(() -> new IllegalArgumentException("Séance introuvable : " + seanceId));
        scopeResolver.verifieAcces(seance.getEquipeId());
        UUID clubId = clubDeSeance(seance);
        List<ProfilImportDto> dtos = new ArrayList<>();
        profilRepository.findByClubId(clubId).forEach(p -> dtos.add(versDto(p)));
        profilRepository.findByClubIdIsNull().forEach(p -> dtos.add(versDto(p)));
        return dtos;
    }

    public void supprimerProfil(UUID profilId, UUID seanceId) {
        Seance seance = seanceRepository.findById(seanceId)
                .orElseThrow(() -> new IllegalArgumentException("Séance introuvable : " + seanceId));
        scopeResolver.verifieAcces(seance.getEquipeId());
        UUID clubId = clubDeSeance(seance);
        ProfilImportGps profil = profilRepository.findById(profilId)
                .orElseThrow(() -> new IllegalArgumentException("Profil introuvable"));
        if (profil.getClubId() == null || !profil.getClubId().equals(clubId)) {
            throw new IllegalArgumentException("Seuls les profils de votre club sont supprimables");
        }
        profilRepository.delete(profil);
    }

    private void sauvegarderProfilClub(UUID clubId, String signature, List<MappingColonne> mappings,
                                       String formatIdentite, String nomProfil, String nomFichier) {
        ProfilImportGps profil = profilRepository
                .findFirstByClubIdAndSignatureEntetes(clubId, signature)
                .orElseGet(ProfilImportGps::new);
        profil.setClubId(clubId);
        profil.setSignatureEntetes(signature);
        if (nomProfil != null && !nomProfil.isBlank()) {
            profil.setNom(nomProfil);
        } else if (profil.getNom() == null) {
            profil.setNom(nomFichier != null ? "Profil " + nomFichier : "Profil du club");
        }
        profil.setFormatIdentite(formatIdentite);
        try {
            profil.setMappings(objectMapper.writeValueAsString(mappings));
        } catch (Exception e) {
            throw new IllegalArgumentException("Mappings invalides");
        }
        profil.setUpdatedAt(java.time.Instant.now());
        profilRepository.save(profil);
    }

    /* ═════════════════════════ CONVERSION + VÉRIFICATIONS ═════════════════════════ */

    private void convertirEtVerifier(Tableau tableau, List<MappingColonne> mappings, String formatIdentite,
                                     Seance seance, UUID clubId, AnalyseImportResponse reponse) {
        // Index en-tête normalisé → position de colonne, puis métrique → position.
        Map<String, Integer> positionParEntete = new HashMap<>();
        for (int i = 0; i < tableau.entetes().size(); i++) {
            positionParEntete.putIfAbsent(normalise(tableau.entetes().get(i)), i);
        }
        Map<MetriqueImport, Integer> positionParMetrique = new EnumMap<>(MetriqueImport.class);
        Map<MetriqueImport, MappingColonne> mappingParMetrique = new EnumMap<>(MetriqueImport.class);
        for (MappingColonne m : mappings) {
            if (m.getMetrique() == null) continue;
            Integer pos = positionParEntete.get(m.getEntete());
            if (pos == null) continue;
            if (positionParMetrique.containsKey(m.getMetrique())) {
                reponse.getAvertissements().add(new AvertissementImportDto("COLONNE", null, m.getEntete(),
                        "Deux colonnes pointent vers la même métrique " + m.getMetrique() + " — seule la première est utilisée"));
                continue;
            }
            positionParMetrique.put(m.getMetrique(), pos);
            mappingParMetrique.put(m.getMetrique(), m);
        }
        Integer posIdentite = positionParMetrique.get(MetriqueImport.IDENTITE);
        if (posIdentite == null) {
            throw new IllegalArgumentException("Aucune colonne identité (nom du joueur) n'est mappée");
        }

        MatchingJoueurs matching = prepareMatching(clubId);
        LocalDate dateFichier = null;

        for (LigneBrute brute : tableau.lignes()) {
            String identite = brute.cellules().get(posIdentite);
            if (identite == null || identite.isBlank()) continue; // lignes TOTAL / MOYENNE sans identité

            LigneGpsImportDto ligne = new LigneGpsImportDto();
            ligne.setNumeroLigne(brute.numero());
            ligne.setIdentiteFichier(identite.trim());

            if (dateFichier == null && positionParMetrique.containsKey(MetriqueImport.DATE_SEANCE)) {
                dateFichier = parseDate(brute.cellules().get(positionParMetrique.get(MetriqueImport.DATE_SEANCE)));
            }

            MappingColonne mDuree = mappingParMetrique.get(MetriqueImport.DUREE);
            if (mDuree != null) {
                BigDecimal duree = ImportNormalisation.parseDureeMinutes(
                        brute.cellules().get(positionParMetrique.get(MetriqueImport.DUREE)), mDuree.getFormatDuree());
                ligne.setDureeMinutes(duree == null ? null : (short) duree.longValue());
            }

            ligne.setDistanceTotaleM(litDecimal(brute, positionParMetrique, mappingParMetrique, MetriqueImport.DISTANCE_TOTALE));
            ligne.setVitesseMaxKmh(litDecimal(brute, positionParMetrique, mappingParMetrique, MetriqueImport.VITESSE_MAX));
            ligne.setRatioDistanceMin(litDecimal(brute, positionParMetrique, mappingParMetrique, MetriqueImport.RATIO_DISTANCE_MIN));
            ligne.setNbSprints24kmh(litShort(brute, positionParMetrique, mappingParMetrique, MetriqueImport.NB_SPRINTS));
            ligne.setNbAccelerations(litShort(brute, positionParMetrique, mappingParMetrique, MetriqueImport.NB_ACCELERATIONS));
            ligne.setNbFreinages(litShort(brute, positionParMetrique, mappingParMetrique, MetriqueImport.NB_FREINAGES));

            // Zones : re-cumul des BANDES vers les colonnes cumulatives, de la plus haute à la plus basse.
            BigDecimal z28 = litDecimal(brute, positionParMetrique, mappingParMetrique, MetriqueImport.DISTANCE_Z28);
            BigDecimal z24 = cumule(litDecimal(brute, positionParMetrique, mappingParMetrique, MetriqueImport.DISTANCE_Z24),
                    mappingParMetrique.get(MetriqueImport.DISTANCE_Z24), z28);
            BigDecimal z19 = cumule(litDecimal(brute, positionParMetrique, mappingParMetrique, MetriqueImport.DISTANCE_Z19),
                    mappingParMetrique.get(MetriqueImport.DISTANCE_Z19), z24);
            BigDecimal z15 = cumule(litDecimal(brute, positionParMetrique, mappingParMetrique, MetriqueImport.DISTANCE_Z15),
                    mappingParMetrique.get(MetriqueImport.DISTANCE_Z15), z19);
            ligne.setDistanceSprint28kmhM(z28);
            ligne.setDistanceSprint24kmhM(z24);
            ligne.setDistance19kmhM(z19);
            ligne.setDistance15kmhM(z15);

            UUID joueurId = matching.trouve(identite, formatIdentite);
            if (joueurId != null) {
                ligne.setJoueurId(joueurId.toString());
                ligne.setJoueurNomAffiche(matching.nomAffiche(joueurId));
            }
            reponse.getLignes().add(ligne);
        }

        if (reponse.getLignes().isEmpty()) {
            throw new IllegalArgumentException("Aucune ligne exploitable (colonne identité vide partout ?)");
        }

        // Joueurs inconnus, dédupliqués, avec découpage prénom/nom suggéré pour le CREATE.
        Set<String> vus = new LinkedHashSet<>();
        for (LigneGpsImportDto l : reponse.getLignes()) {
            if (l.getJoueurId() == null && vus.add(l.getIdentiteFichier())) {
                String[] decoupe = decoupeIdentite(l.getIdentiteFichier(), formatIdentite);
                reponse.getJoueursInconnus().add(new JoueurInconnuDto(l.getIdentiteFichier(), decoupe[0], decoupe[1]));
            }
        }

        verifie(reponse, seance, dateFichier);
    }

    private BigDecimal litDecimal(LigneBrute brute, Map<MetriqueImport, Integer> positions,
                                  Map<MetriqueImport, MappingColonne> mappings, MetriqueImport metrique) {
        Integer pos = positions.get(metrique);
        if (pos == null) return null;
        BigDecimal valeur = ImportNormalisation.parseNombre(brute.cellules().get(pos));
        if (valeur == null) return null;
        return valeur.multiply(BigDecimal.valueOf(mappings.get(metrique).facteurOuUn()))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private Short litShort(LigneBrute brute, Map<MetriqueImport, Integer> positions,
                           Map<MetriqueImport, MappingColonne> mappings, MetriqueImport metrique) {
        BigDecimal valeur = litDecimal(brute, positions, mappings, metrique);
        return valeur == null ? null : (short) valeur.longValue();
    }

    /** BANDE : la distance de la plage s'ajoute au cumul de la zone supérieure ; CUMUL : inchangé. */
    private BigDecimal cumule(BigDecimal valeur, MappingColonne mapping, BigDecimal cumulSuperieur) {
        if (valeur == null || mapping == null) return valeur;
        if (!"BANDE".equals(mapping.getSemantique())) return valeur;
        return valeur.add(cumulSuperieur == null ? BigDecimal.ZERO : cumulSuperieur);
    }

    /* ── Vérifications de vraisemblance (non bloquantes) ── */

    private void verifie(AnalyseImportResponse reponse, Seance seance, LocalDate dateFichier) {
        List<AvertissementImportDto> avert = reponse.getAvertissements();
        List<LigneGpsImportDto> lignes = reponse.getLignes();

        // FICHIER : date du fichier ≠ date de la séance choisie (mauvaise séance ?).
        if (dateFichier != null && seance.getDate() != null && !dateFichier.equals(seance.getDate())) {
            avert.add(new AvertissementImportDto("FICHIER", null, null,
                    "Le fichier date du " + dateFichier + " mais la séance sélectionnée est le "
                            + seance.getDate() + " — vérifiez la séance choisie"));
        }

        // COLONNE : moyennes hors plages plausibles → mapping probablement décalé.
        verifieMoyenne(avert, lignes, LigneGpsImportDto::getDistanceTotaleM, 500, 16000,
                "Distance totale moyenne suspecte", "distance totale");
        verifieMoyenne(avert, lignes, LigneGpsImportDto::getVitesseMaxKmh, 12, 40,
                "Vitesse max moyenne suspecte", "vitesse max");
        verifieMoyenne(avert, lignes,
                l -> l.getDureeMinutes() == null ? null : BigDecimal.valueOf(l.getDureeMinutes()), 10, 200,
                "Durée moyenne suspecte", "durée");
        long violationsMonotonie = lignes.stream().filter(this::monotonieViolee).count();
        if (violationsMonotonie * 2 > lignes.size()) {
            avert.add(new AvertissementImportDto("COLONNE", null, null,
                    "Les distances par zone ne décroissent pas (>15 ≥ >19 ≥ >24 ≥ >28) sur la majorité des lignes — mapping des zones probablement inversé"));
        }

        // LIGNE : données aberrantes par joueur.
        Map<UUID, double[]> historiques = chargeHistoriques(lignes);
        for (LigneGpsImportDto l : lignes) {
            verifieLigne(avert, l, historiques);
        }
    }

    private void verifieMoyenne(List<AvertissementImportDto> avert, List<LigneGpsImportDto> lignes,
                                java.util.function.Function<LigneGpsImportDto, BigDecimal> extracteur,
                                double min, double max, String message, String colonne) {
        double[] valeurs = lignes.stream().map(extracteur).filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue).toArray();
        if (valeurs.length == 0) return;
        double moyenne = Arrays.stream(valeurs).average().orElse(0);
        if (moyenne < min || moyenne > max) {
            avert.add(new AvertissementImportDto("COLONNE", null, colonne,
                    message + " (" + Math.round(moyenne) + ", plage attendue " + Math.round(min) + "–" + Math.round(max) + ") — vérifiez le mapping et l'unité"));
        }
    }

    private boolean monotonieViolee(LigneGpsImportDto l) {
        return decroissanceViolee(l.getDistance15kmhM(), l.getDistance19kmhM())
                || decroissanceViolee(l.getDistance19kmhM(), l.getDistanceSprint24kmhM())
                || decroissanceViolee(l.getDistanceSprint24kmhM(), l.getDistanceSprint28kmhM());
    }

    private boolean decroissanceViolee(BigDecimal grand, BigDecimal petit) {
        return grand != null && petit != null && grand.compareTo(petit) < 0;
    }

    private void verifieLigne(List<AvertissementImportDto> avert, LigneGpsImportDto l,
                              Map<UUID, double[]> historiques) {
        int n = l.getNumeroLigne() == null ? -1 : l.getNumeroLigne();
        String qui = l.getIdentiteFichier();

        for (var v : List.of(
                Map.entry("distance totale", Optional.ofNullable(l.getDistanceTotaleM())),
                Map.entry("distance >15", Optional.ofNullable(l.getDistance15kmhM())),
                Map.entry("distance >19", Optional.ofNullable(l.getDistance19kmhM())),
                Map.entry("distance >24", Optional.ofNullable(l.getDistanceSprint24kmhM())),
                Map.entry("distance >28", Optional.ofNullable(l.getDistanceSprint28kmhM())),
                Map.entry("vitesse max", Optional.ofNullable(l.getVitesseMaxKmh())))) {
            if (v.getValue().isPresent() && v.getValue().get().signum() < 0) {
                avert.add(new AvertissementImportDto("LIGNE", n, v.getKey(),
                        qui + " : valeur négative (" + v.getValue().get() + ")"));
            }
        }
        if (l.getVitesseMaxKmh() != null && (l.getVitesseMaxKmh().doubleValue() < 12 || l.getVitesseMaxKmh().doubleValue() > 40)) {
            avert.add(new AvertissementImportDto("LIGNE", n, "vitesse max",
                    qui + " : vitesse max improbable (" + l.getVitesseMaxKmh() + " km/h)"));
        }
        if (l.getDureeMinutes() != null && (l.getDureeMinutes() < 10 || l.getDureeMinutes() > 200)) {
            avert.add(new AvertissementImportDto("LIGNE", n, "durée",
                    qui + " : durée improbable (" + l.getDureeMinutes() + " min)"));
        }
        if (l.getDistanceTotaleM() != null && (l.getDistanceTotaleM().doubleValue() < 500 || l.getDistanceTotaleM().doubleValue() > 16000)) {
            avert.add(new AvertissementImportDto("LIGNE", n, "distance totale",
                    qui + " : distance improbable (" + l.getDistanceTotaleM() + " m)"));
        }
        if (l.getDistance15kmhM() != null && l.getDistanceTotaleM() != null
                && l.getDistance15kmhM().compareTo(l.getDistanceTotaleM()) > 0) {
            avert.add(new AvertissementImportDto("LIGNE", n, "distance >15",
                    qui + " : distance en zone supérieure à la distance totale"));
        }
        if (monotonieViolee(l)) {
            avert.add(new AvertissementImportDto("LIGNE", n, null,
                    qui + " : distances par zone incohérentes (une zone haute dépasse une zone basse)"));
        }
        // Écart extrême vs les habitudes du joueur (≥ 3 séances d'historique).
        if (l.getJoueurId() != null && l.getDistanceTotaleM() != null) {
            double[] stats = historiques.get(UUID.fromString(l.getJoueurId()));
            if (stats != null && stats[0] >= 3 && stats[1] > 0) {
                double ratio = l.getDistanceTotaleM().doubleValue() / stats[1];
                if (ratio > 3 || ratio < 1.0 / 3) {
                    avert.add(new AvertissementImportDto("LIGNE", n, "distance totale",
                            qui + " : distance très éloignée de ses habitudes (" + l.getDistanceTotaleM()
                                    + " m contre " + Math.round(stats[1]) + " m en moyenne)"));
                }
            }
        }
    }

    private Map<UUID, double[]> chargeHistoriques(List<LigneGpsImportDto> lignes) {
        Set<UUID> ids = new HashSet<>();
        for (LigneGpsImportDto l : lignes) {
            if (l.getJoueurId() != null) ids.add(UUID.fromString(l.getJoueurId()));
        }
        Map<UUID, double[]> stats = new HashMap<>();
        if (ids.isEmpty()) return stats;
        for (DonneeGpsRepository.DistanceAgg agg : donneeGpsRepository.aggregerDistances(ids)) {
            stats.put(agg.getJoueurId(), new double[]{agg.getNb(), agg.getDistanceMoyenne() == null ? 0 : agg.getDistanceMoyenne()});
        }
        return stats;
    }

    /* ═════════════════════════ MATCHING JOUEUR ═════════════════════════ */

    private MatchingJoueurs prepareMatching(UUID clubId) {
        List<Joueur> joueurs = joueurRepository.findByClubId(clubId);
        Map<String, UUID> alias = new HashMap<>();
        for (AliasJoueurImport a : aliasRepository.findByClubId(clubId)) {
            alias.put(a.getAlias(), a.getJoueurId());
        }
        return new MatchingJoueurs(joueurs, alias);
    }

    private void memoriseAlias(UUID clubId, String identiteFichier, UUID joueurId) {
        String cle = normalise(identiteFichier);
        if (cle.isBlank()) return;
        aliasRepository.findByClubIdAndAlias(clubId, cle).orElseGet(() -> {
            AliasJoueurImport a = new AliasJoueurImport();
            a.setClubId(clubId);
            a.setAlias(cle);
            a.setJoueurId(joueurId);
            return aliasRepository.save(a);
        });
    }

    /** Index de résolution : alias mémorisés, puis nom complet (2 ordres), puis prénom seul si unique. */
    private static class MatchingJoueurs {
        private final Map<String, UUID> alias;
        private final Map<String, UUID> parNomComplet = new HashMap<>();
        private final Set<String> nomsAmbigus = new HashSet<>();
        private final Map<String, List<UUID>> parPrenom = new HashMap<>();
        private final Map<UUID, String> affichage = new HashMap<>();

        MatchingJoueurs(List<Joueur> joueurs, Map<String, UUID> alias) {
            this.alias = alias;
            for (Joueur j : joueurs) {
                String prenom = j.getPrenom() == null ? "" : j.getPrenom();
                String nom = j.getNom() == null ? "" : j.getNom();
                affichage.put(j.getId(), (prenom + " " + nom).trim());
                if (!nom.isBlank()) {
                    indexe(normalise(prenom + " " + nom), j.getId());
                    indexe(normalise(nom + " " + prenom), j.getId());
                }
                parPrenom.computeIfAbsent(normalise(prenom), k -> new ArrayList<>()).add(j.getId());
            }
        }

        private void indexe(String cle, UUID id) {
            if (cle.isBlank()) return;
            if (parNomComplet.containsKey(cle) && !parNomComplet.get(cle).equals(id)) {
                nomsAmbigus.add(cle);
            } else {
                parNomComplet.put(cle, id);
            }
        }

        UUID trouve(String identite, String formatIdentite) {
            String cle = normalise(identite);
            if (cle.isBlank()) return null;
            UUID viaAlias = alias.get(cle);
            if (viaAlias != null) return viaAlias;
            if (!nomsAmbigus.contains(cle)) {
                UUID complet = parNomComplet.get(cle);
                if (complet != null) return complet;
            }
            // Prénom seul : uniquement si non ambigu dans le club.
            if ("PRENOM".equals(formatIdentite) || !cle.contains(" ")) {
                List<UUID> candidats = parPrenom.get(cle);
                if (candidats != null && candidats.size() == 1) return candidats.get(0);
            }
            return null;
        }

        String nomAffiche(UUID id) {
            return affichage.get(id);
        }
    }

    private String[] decoupeIdentite(String identite, String formatIdentite) {
        String[] tokens = identite.trim().split("\\s+");
        if (tokens.length < 2) return new String[]{identite.trim(), ""};
        if ("NOM_PRENOM".equals(formatIdentite)) {
            return new String[]{tokens[tokens.length - 1],
                    String.join(" ", Arrays.copyOfRange(tokens, 0, tokens.length - 1))};
        }
        return new String[]{tokens[0], String.join(" ", Arrays.copyOfRange(tokens, 1, tokens.length))};
    }

    /* ═════════════════════════ DÉTECTION COLONNES / FORMAT ═════════════════════════ */

    private List<ColonneDetecteeDto> detecteColonnes(Tableau tableau) {
        List<ColonneDetecteeDto> colonnes = new ArrayList<>();
        for (int c = 0; c < tableau.entetes().size(); c++) {
            ColonneDetecteeDto dto = new ColonneDetecteeDto();
            dto.setEntete(tableau.entetes().get(c));
            dto.setEnteteNormalise(normalise(tableau.entetes().get(c)));
            List<String> apercu = new ArrayList<>();
            for (LigneBrute l : tableau.lignes()) {
                String v = l.cellules().get(c);
                if (v != null && !v.isBlank()) apercu.add(v);
                if (apercu.size() == 3) break;
            }
            dto.setApercu(apercu);
            dto.setSuggestion(dictionnaire.suggerer(dto.getEnteteNormalise(), apercu));
            colonnes.add(dto);
        }
        return colonnes;
    }

    /** Devine le format d'identité depuis la colonne suggérée IDENTITE (« Brian FENEUIL » → PRENOM_NOM). */
    private String suggereFormatIdentite(Tableau tableau, List<ColonneDetecteeDto> colonnes) {
        for (ColonneDetecteeDto c : colonnes) {
            if (c.getSuggestion() == null || c.getSuggestion().getMetrique() != MetriqueImport.IDENTITE) continue;
            for (String valeur : c.getApercu()) {
                String[] tokens = valeur.trim().split("\\s+");
                if (tokens.length < 2) return "PRENOM";
                if (tokens[0].equals(tokens[0].toUpperCase(Locale.ROOT)) && tokens[0].length() > 1) return "NOM_PRENOM";
                return "PRENOM_NOM";
            }
        }
        return "PRENOM_NOM";
    }

    /* ═════════════════════════ DIVERS ═════════════════════════ */

    private UUID clubDeSeance(Seance seance) {
        if (seance.getEquipeId() == null) {
            throw new IllegalArgumentException("Séance sans équipe : import impossible");
        }
        return equipeRepository.findById(seance.getEquipeId())
                .orElseThrow(() -> new IllegalArgumentException("Équipe introuvable"))
                .getClubId();
    }

    private List<MappingColonne> parseMappings(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<MappingColonne>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Mappings illisibles : " + e.getMessage());
        }
    }

    private ProfilImportDto versDto(ProfilImportGps p) {
        return new ProfilImportDto(p.getId().toString(), p.getNom(), p.getClubId() == null,
                p.getFormatIdentite(), parseMappings(p.getMappings()));
    }

    private LocalDate parseDate(String brut) {
        if (brut == null || brut.isBlank()) return null;
        String s = brut.trim();
        try { return OffsetDateTime.parse(s).toLocalDate(); } catch (Exception ignored) {}
        try { return java.time.LocalDateTime.parse(s).toLocalDate(); } catch (Exception ignored) {}
        try { return LocalDate.parse(s); } catch (Exception ignored) {}
        try { return LocalDate.parse(s, DateTimeFormatter.ofPattern("dd/MM/yyyy")); } catch (Exception ignored) {}
        return null;
    }
}
