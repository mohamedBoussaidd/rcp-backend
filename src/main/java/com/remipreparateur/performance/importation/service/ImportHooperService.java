package com.remipreparateur.performance.importation.service;

import com.remipreparateur.club.repository.EquipeRepository;
import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.joueur.service.JoueurService;
import com.remipreparateur.medical.wellness.entity.WellnessQuotidien;
import com.remipreparateur.medical.wellness.repository.WellnessQuotidienRepository;
import com.remipreparateur.performance.importation.dto.*;
import com.remipreparateur.performance.importation.entity.AliasJoueurImport;
import com.remipreparateur.performance.importation.repository.AliasJoueurImportRepository;
import com.remipreparateur.performance.importation.service.LecteurFichierTabulaire.LigneBrute;
import com.remipreparateur.performance.importation.service.LecteurFichierTabulaire.Tableau;
import com.remipreparateur.saison.service.AppartenanceService;
import com.remipreparateur.shared.security.ScopeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.remipreparateur.performance.importation.service.ImportNormalisation.normalise;

/**
 * Import du ressenti quotidien (indice de Hooper) depuis un export « playermonitoring » (CSV/xlsx).
 *
 * <p>Réutilise les rails des imports GPS/RPE (lecture tolérante à l'encodage, {@link AppariementJoueur}
 * et alias par club) mais vise la table {@code wellness_quotidien} (clé joueur + date). Deux
 * conversions propres à cet export :
 * <ul>
 *   <li><b>Inversion d'échelle</b> : l'export note 10 = bon / 1 = mauvais sur les items positifs
 *       (énergie, fraîcheur, humeur, sommeil) ; l'app est l'inverse (1 = bon → 10 = mauvais) →
 *       on applique {@code 11 − valeur}.</li>
 *   <li><b>Colonnes → items app</b> : Sommeil→sommeil, Humeur→humeur, Energie Générale→fatigue,
 *       Fraîcheur Musculaire→douleur (courbatures). Le {@code stress} n'existe pas dans l'export
 *       → valeur neutre 5 à la création (préservée si une saisie existe déjà). La douleur
 *       localisée (Emplacement + Douleur) alimente la gêne, SANS alerte médicale.</li>
 * </ul>
 * La date-clé est celle de « Date de la séance » (le jour du monitoring), jamais la date de réponse.
 */
@Service
@RequiredArgsConstructor
public class ImportHooperService {

    /** Stress non mesuré dans l'export → neutre à la création (jamais écrasé sur une saisie existante). */
    private static final short STRESS_DEFAUT = 5;
    /** « mar. 21/07/2026 08:00 » → capture le jj/mm/aaaa. */
    private static final Pattern DATE_FR = Pattern.compile("(\\d{1,2})/(\\d{1,2})/(\\d{4})");

    private final LecteurFichierTabulaire lecteur;
    private final AliasJoueurImportRepository aliasRepository;
    private final EquipeRepository equipeRepository;
    private final JoueurRepository joueurRepository;
    private final JoueurService joueurService;
    private final WellnessQuotidienRepository wellnessRepository;
    private final AppartenanceService appartenance;
    private final ScopeResolver scopeResolver;

    /* ═════════════════════════ ANALYSE ═════════════════════════ */

    public AnalyseImportHooperResponse analyser(byte[] contenu, String nomFichier, String texteColle, UUID equipeId) {
        scopeResolver.verifieAcces(equipeId);
        UUID clubId = equipeRepository.findById(equipeId)
                .orElseThrow(() -> new IllegalArgumentException("Équipe introuvable")).getClubId();

        Tableau tableau = texteColle != null && !texteColle.isBlank()
                ? lecteur.lireTexteColle(texteColle)
                : lecteur.lire(contenu, nomFichier);

        Colonnes cols = detecteColonnes(tableau.entetes());
        if (cols.identite < 0) {
            throw new IllegalArgumentException("Colonne « Nom du joueur » introuvable. En-têtes lus : "
                    + String.join(", ", tableau.entetes()));
        }
        List<String> manquantes = new ArrayList<>();
        if (cols.energie < 0)   manquantes.add("Energie Générale");
        if (cols.fraicheur < 0) manquantes.add("Fraicheur Musculaire");
        if (cols.humeur < 0)    manquantes.add("Humeur");
        if (cols.sommeil < 0)   manquantes.add("Sommeil");
        if (!manquantes.isEmpty()) {
            throw new IllegalArgumentException("Colonnes de ressenti introuvables : " + String.join(", ", manquantes)
                    + ". En-têtes lus : " + String.join(", ", tableau.entetes()));
        }

        AnalyseImportHooperResponse reponse = new AnalyseImportHooperResponse();
        reponse.setEquipeId(equipeId.toString());
        if (cols.date < 0) {
            reponse.getAvertissements().add(new AvertissementImportDto("FICHIER", null, null,
                    "Aucune colonne « Date de la séance » : impossible de dater le ressenti, l'import ne pourra rien enregistrer."));
        }

        AppariementJoueur appariement = new AppariementJoueur(joueurRepository.findByClubId(clubId), aliasMap(clubId));
        LocalDate dateFichier = null;

        for (LigneBrute brute : tableau.lignes()) {
            String identite = cellule(brute, cols.identite);
            if (identite == null || identite.isBlank()) continue; // lignes TOTAL / vides sans identité

            LocalDate dateLigne = cols.date >= 0 ? parseDateFr(cellule(brute, cols.date)) : null;
            if (dateLigne != null && dateFichier == null) dateFichier = dateLigne;
            if (dateLigne == null) dateLigne = dateFichier; // repli sur la date du fichier

            LigneHooperImportDto ligne = new LigneHooperImportDto();
            ligne.setNumeroLigne(brute.numero());
            ligne.setIdentiteFichier(identite.trim());
            ligne.setDate(dateLigne);

            // Les 4 items de ressenti (échelle export), null si absent.
            Short energie   = litItem(cellule(brute, cols.energie),   brute.numero(), identite, "Energie Générale",   reponse.getAvertissements());
            Short fraicheur = litItem(cellule(brute, cols.fraicheur), brute.numero(), identite, "Fraicheur Musculaire", reponse.getAvertissements());
            Short humeur    = litItem(cellule(brute, cols.humeur),    brute.numero(), identite, "Humeur",              reponse.getAvertissements());
            Short sommeil   = litItem(cellule(brute, cols.sommeil),   brute.numero(), identite, "Sommeil",             reponse.getAvertissements());

            int presents = (energie != null ? 1 : 0) + (fraicheur != null ? 1 : 0)
                    + (humeur != null ? 1 : 0) + (sommeil != null ? 1 : 0);
            boolean repondu = presents == 4;
            ligne.setRepondu(repondu);

            if (repondu) {
                // Convention app : 1 = bon → 10 = mauvais. L'export est inversé → 11 − valeur.
                ligne.setSommeil(inverse(sommeil));
                ligne.setFatigue(inverse(energie));   // Energie Générale → fatigue générale
                ligne.setDouleur(inverse(fraicheur)); // Fraîcheur Musculaire → douleurs musculaires
                ligne.setHumeur(inverse(humeur));
                ligne.setStress(STRESS_DEFAUT);       // non mesuré dans l'export
                // Gêne localisée : Emplacement + Douleur (0 ou vide = aucune gêne, cf. cas « Cheville / 0 »).
                String zone = cols.emplacement >= 0 ? blancEnNull(cellule(brute, cols.emplacement)) : null;
                Short intensite = cols.douleur >= 0
                        ? litGeneIntensite(cellule(brute, cols.douleur), brute.numero(), identite, reponse.getAvertissements())
                        : null;
                if (zone != null && intensite != null) {
                    ligne.setGeneZone(zone.length() > 40 ? zone.substring(0, 40) : zone);
                    ligne.setGeneIntensite(intensite);
                }
                if (presents == 4 && dateLigne == null) {
                    reponse.getAvertissements().add(new AvertissementImportDto("LIGNE", brute.numero(), null,
                            identite.trim() + " : ressenti sans date exploitable — non importé."));
                }
            } else if (presents > 0) {
                reponse.getAvertissements().add(new AvertissementImportDto("LIGNE", brute.numero(), null,
                        identite.trim() + " : réponse incomplète (" + presents + "/4 items) — non importée."));
            }

            UUID joueurId = appariement.trouve(identite);
            if (joueurId != null) {
                ligne.setJoueurId(joueurId.toString());
                ligne.setJoueurNomAffiche(appariement.nomAffiche(joueurId));
            }

            if (repondu) reponse.setNbRepondants(reponse.getNbRepondants() + 1);
            else reponse.setNbSansReponse(reponse.getNbSansReponse() + 1);
            reponse.getLignes().add(ligne);
        }

        if (reponse.getLignes().isEmpty()) {
            throw new IllegalArgumentException("Aucune ligne exploitable (colonne « Nom du joueur » vide partout ?)");
        }

        // Joueurs inconnus, dédupliqués, uniquement parmi les répondants (les seuls à importer).
        Set<String> vus = new LinkedHashSet<>();
        for (LigneHooperImportDto l : reponse.getLignes()) {
            if (l.isRepondu() && l.getJoueurId() == null && vus.add(l.getIdentiteFichier())) {
                String[] d = AppariementJoueur.decoupeNomPrenom(l.getIdentiteFichier());
                reponse.getJoueursInconnus().add(new JoueurInconnuDto(l.getIdentiteFichier(), d[1], d[0]));
            }
        }

        if (reponse.getNbSansReponse() > 0) {
            reponse.getAvertissements().add(new AvertissementImportDto("FICHIER", null, null,
                    reponse.getNbSansReponse() + " joueur(s) sans réponse : non importés."));
        }

        reponse.setStatut("PRET");
        return reponse;
    }

    /* ═════════════════════════ CONFIRMATION ═════════════════════════ */

    @Transactional
    public Map<String, Object> confirmer(ConfirmerImportHooperRequest request) {
        UUID equipeId = UUID.fromString(request.getEquipeId());
        scopeResolver.verifieAcces(equipeId);
        UUID clubId = equipeRepository.findById(equipeId)
                .orElseThrow(() -> new IllegalArgumentException("Équipe introuvable")).getClubId();

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
                            throw new IllegalArgumentException("Joueur hors du club de l'équipe");
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
        for (LigneHooperImportDto ligne : request.getLignes()) {
            if (!ligne.isRepondu() || ligne.getDate() == null) continue; // sans réponse ou sans date : jamais importée

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

            // Une fiche qui reçoit du ressenti doit être dans l'effectif de la saison (miroir import GPS/RPE).
            joueurService.inscrireEffectifSiHorsSaison(joueur, equipeId);

            Optional<WellnessQuotidien> existant = wellnessRepository.findByJoueurIdAndDate(joueurId, ligne.getDate());
            boolean nouveau = existant.isEmpty();
            WellnessQuotidien w = existant.orElseGet(WellnessQuotidien::new);
            w.setJoueurId(joueurId);
            w.setEquipeId(appartenance.equipePrincipale(joueurId)); // équipe dérivée de l'effectif (comme la PWA)
            w.setDate(ligne.getDate());
            // Items importés (déjà convertis) — écrasent toujours la valeur du jour.
            w.setSommeil(ligne.getSommeil());
            w.setFatigue(ligne.getFatigue());
            w.setDouleur(ligne.getDouleur());
            w.setHumeur(ligne.getHumeur());
            // Stress non mesuré : neutre à la création, PRÉSERVÉ si une saisie (ex. PWA) existe déjà.
            if (nouveau) {
                w.setStress(ligne.getStress() != null ? ligne.getStress() : STRESS_DEFAUT);
            }
            // Gêne : posée UNIQUEMENT si l'import en porte une (sinon on laisse une éventuelle gêne PWA).
            // Import de masse → aucune notification médicale (contrairement à la saisie PWA).
            if (ligne.getGeneZone() != null) {
                w.setGeneZone(ligne.getGeneZone());
                w.setGeneIntensite(ligne.getGeneIntensite());
                w.setGeneMoment(null); // moment non fourni par l'export
                w.setGeneTraitee(false);
                w.setGeneTraiteePar(null);
                w.setGeneTraiteeLe(null);
                w.setGeneResolution(null);
            }
            wellnessRepository.save(w);
            inseres++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("equipeId", equipeId.toString());
        result.put("inseres", inseres);
        result.put("ignores", ignores);
        return result;
    }

    /* ═════════════════════════ DÉTECTION COLONNES ═════════════════════════ */

    /** Positions des colonnes utiles (-1 = absente). Détection par en-tête, tolérante au mojibake. */
    private record Colonnes(int identite, int date, int energie, int fraicheur, int humeur,
                            int sommeil, int emplacement, int douleur) {}

    private Colonnes detecteColonnes(List<String> entetes) {
        int identite = -1, date = -1, dateSeance = -1, energie = -1, fraicheur = -1,
                humeur = -1, sommeil = -1, emplacement = -1, douleur = -1;
        for (int i = 0; i < entetes.size(); i++) {
            String n = normalise(entetes.get(i));
            if (identite < 0 && n.contains("nom") && n.contains("joueur")) identite = i;
            if (dateSeance < 0 && n.contains("date") && n.contains("seance")) dateSeance = i;
            if (date < 0 && n.contains("date")) date = i;
            if (energie < 0 && n.contains("energie")) energie = i;
            if (fraicheur < 0 && n.contains("fraicheur")) fraicheur = i;
            if (humeur < 0 && n.contains("humeur")) humeur = i;
            if (sommeil < 0 && n.contains("sommeil")) sommeil = i;
            if (emplacement < 0 && n.contains("emplacement")) emplacement = i;
            if (douleur < 0 && n.contains("douleur") && !n.contains("emplacement")) douleur = i;
        }
        // Priorité à « Date de la séance » (jour du monitoring) sur « Date de réponse ».
        return new Colonnes(identite, dateSeance >= 0 ? dateSeance : date,
                energie, fraicheur, humeur, sommeil, emplacement, douleur);
    }

    /* ═════════════════════════ MATCHING JOUEUR ═════════════════════════ */

    private Map<String, UUID> aliasMap(UUID clubId) {
        Map<String, UUID> alias = new HashMap<>();
        for (AliasJoueurImport a : aliasRepository.findByClubId(clubId)) {
            alias.put(a.getAlias(), a.getJoueurId());
        }
        return alias;
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

    /* ═════════════════════════ DIVERS ═════════════════════════ */

    private String cellule(LigneBrute brute, int position) {
        if (position < 0 || position >= brute.cellules().size()) return null;
        return brute.cellules().get(position);
    }

    /** Inversion d'échelle 1..10 : export (10 = bon) → app (1 = bon). */
    private short inverse(short valeurExport) {
        return (short) (11 - valeurExport);
    }

    /** Item de ressenti 1..10 ; null si vide. Hors plage → avertissement + null (non importé). */
    private Short litItem(String brut, int numeroLigne, String qui, String colonne,
                          List<AvertissementImportDto> avert) {
        BigDecimal n = ImportNormalisation.parseNombre(brut);
        if (n == null) return null;
        int v = n.setScale(0, java.math.RoundingMode.HALF_UP).intValue();
        if (v < 1 || v > 10) {
            avert.add(new AvertissementImportDto("LIGNE", numeroLigne, colonne,
                    qui + " : " + colonne + " hors plage 1–10 (" + brut + ") — ignoré."));
            return null;
        }
        return (short) v;
    }

    /**
     * Intensité de la gêne 1..10 depuis la colonne « Douleur ». 0 ou vide = aucune gêne (null,
     * sans avertissement — cas légitime « Cheville / 0 »). >10 → avertissement + plafonné à 10.
     */
    private Short litGeneIntensite(String brut, int numeroLigne, String qui,
                                   List<AvertissementImportDto> avert) {
        BigDecimal n = ImportNormalisation.parseNombre(brut);
        if (n == null) return null;
        int v = n.setScale(0, java.math.RoundingMode.HALF_UP).intValue();
        if (v < 1) return null; // 0 = pas de gêne
        if (v > 10) {
            avert.add(new AvertissementImportDto("LIGNE", numeroLigne, "Douleur",
                    qui + " : intensité de douleur > 10 (" + brut + ") — plafonnée à 10."));
            return (short) 10;
        }
        return (short) v;
    }

    private String blancEnNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private LocalDate parseDateFr(String brut) {
        if (brut == null) return null;
        Matcher m = DATE_FR.matcher(brut);
        if (!m.find()) return null;
        try {
            return LocalDate.parse(String.format("%02d/%02d/%s",
                    Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), m.group(3)),
                    DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception e) {
            return null;
        }
    }
}
