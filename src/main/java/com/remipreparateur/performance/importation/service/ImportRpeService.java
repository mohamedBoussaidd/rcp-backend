package com.remipreparateur.performance.importation.service;

import com.remipreparateur.club.repository.EquipeRepository;
import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.joueur.service.JoueurService;
import com.remipreparateur.performance.importation.dto.*;
import com.remipreparateur.performance.importation.entity.AliasJoueurImport;
import com.remipreparateur.performance.importation.repository.AliasJoueurImportRepository;
import com.remipreparateur.performance.importation.service.LecteurFichierTabulaire.LigneBrute;
import com.remipreparateur.performance.importation.service.LecteurFichierTabulaire.Tableau;
import com.remipreparateur.performance.rpe.entity.RpeSeance;
import com.remipreparateur.performance.rpe.repository.RpeSeanceRepository;
import com.remipreparateur.performance.seance.entity.Seance;
import com.remipreparateur.performance.seance.repository.SeanceRepository;
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
 * Import du RPE/ressenti post-séance depuis un fichier questionnaire (CSV/xlsx).
 *
 * <p>Réutilise les rails de l'import GPS (lecture tabulaire tolérante à l'encodage, alias joueur
 * mémorisés par club) mais SANS mapping : le format des exports est fixe, les colonnes sont
 * détectées par en-tête. On importe DANS une séance existante — la durée, la date et l'équipe
 * en sont héritées (intégrité), et {@code charge = rpe × durée} est le sRPE lu par l'ACWR.
 */
@Service
@RequiredArgsConstructor
public class ImportRpeService {

    private static final String TYPE_PHYSIQUE = "PHYSIQUE";
    /** « lun. 13/07/2026 11:00 » → capture le jj/mm/aaaa. */
    private static final Pattern DATE_FR = Pattern.compile("(\\d{1,2})/(\\d{1,2})/(\\d{4})");

    private final LecteurFichierTabulaire lecteur;
    private final AliasJoueurImportRepository aliasRepository;
    private final SeanceRepository seanceRepository;
    private final EquipeRepository equipeRepository;
    private final JoueurRepository joueurRepository;
    private final JoueurService joueurService;
    private final RpeSeanceRepository rpeRepository;
    private final ScopeResolver scopeResolver;

    /* ═════════════════════════ ANALYSE ═════════════════════════ */

    public AnalyseImportRpeResponse analyser(byte[] contenu, String nomFichier, String texteColle, UUID seanceId) {
        Seance seance = seanceRepository.findById(seanceId)
                .orElseThrow(() -> new IllegalArgumentException("Séance introuvable : " + seanceId));
        scopeResolver.verifieAcces(seance.getEquipeId());
        UUID clubId = clubDeSeance(seance);

        Tableau tableau = texteColle != null && !texteColle.isBlank()
                ? lecteur.lireTexteColle(texteColle)
                : lecteur.lire(contenu, nomFichier);

        Colonnes cols = detecteColonnes(tableau.entetes());
        if (cols.identite < 0) {
            throw new IllegalArgumentException("Colonne « Nom du joueur » introuvable. En-têtes lus : "
                    + String.join(", ", tableau.entetes()));
        }
        if (cols.rpe < 0) {
            throw new IllegalArgumentException("Colonne « RPE » introuvable. En-têtes lus : "
                    + String.join(", ", tableau.entetes()));
        }

        AnalyseImportRpeResponse reponse = new AnalyseImportRpeResponse();
        reponse.setSeanceId(seanceId.toString());
        Short dureeSeance = seance.getDureeMinutes();
        reponse.setDureeSeance(dureeSeance);
        if (dureeSeance == null) {
            reponse.getAvertissements().add(new AvertissementImportDto("FICHIER", null, null,
                    "La séance n'a pas de durée renseignée : la charge sRPE (RPE × durée) ne sera pas calculée."));
        }

        AppariementJoueur appariement = new AppariementJoueur(joueurRepository.findByClubId(clubId), aliasMap(clubId));
        LocalDate dateFichier = null;

        for (LigneBrute brute : tableau.lignes()) {
            String identite = cellule(brute, cols.identite);
            if (identite == null || identite.isBlank()) continue; // lignes TOTAL / vides sans identité

            if (dateFichier == null && cols.date >= 0) {
                dateFichier = parseDateFr(cellule(brute, cols.date));
            }

            LigneRpeImportDto ligne = new LigneRpeImportDto();
            ligne.setNumeroLigne(brute.numero());
            ligne.setIdentiteFichier(identite.trim());
            ligne.setDureeMinutes(dureeSeance);

            Short rpe = litNote(cellule(brute, cols.rpe), brute.numero(), identite, "RPE", reponse.getAvertissements());
            ligne.setRpe(rpe);
            ligne.setRepondu(rpe != null);
            if (cols.plaisir >= 0) {
                ligne.setPlaisir(litNote(cellule(brute, cols.plaisir), brute.numero(), identite, "plaisir",
                        reponse.getAvertissements()));
            }
            if (rpe != null && dureeSeance != null) {
                ligne.setCharge(rpe * dureeSeance);
            }

            UUID joueurId = appariement.trouve(identite);
            if (joueurId != null) {
                ligne.setJoueurId(joueurId.toString());
                ligne.setJoueurNomAffiche(appariement.nomAffiche(joueurId));
            }

            if (rpe != null) reponse.setNbRepondants(reponse.getNbRepondants() + 1);
            else reponse.setNbSansReponse(reponse.getNbSansReponse() + 1);
            reponse.getLignes().add(ligne);
        }

        if (reponse.getLignes().isEmpty()) {
            throw new IllegalArgumentException("Aucune ligne exploitable (colonne « Nom du joueur » vide partout ?)");
        }

        // Joueurs inconnus, dédupliqués, uniquement parmi les répondants (les seuls à importer).
        Set<String> vus = new LinkedHashSet<>();
        for (LigneRpeImportDto l : reponse.getLignes()) {
            if (l.isRepondu() && l.getJoueurId() == null && vus.add(l.getIdentiteFichier())) {
                String[] d = AppariementJoueur.decoupeNomPrenom(l.getIdentiteFichier());
                reponse.getJoueursInconnus().add(new JoueurInconnuDto(l.getIdentiteFichier(), d[1], d[0]));
            }
        }

        // Garde-fou : le fichier concerne-t-il bien la séance choisie ?
        if (dateFichier != null && seance.getDate() != null && !dateFichier.equals(seance.getDate())) {
            reponse.getAvertissements().add(new AvertissementImportDto("FICHIER", null, null,
                    "Le fichier date du " + dateFichier + " mais la séance sélectionnée est le "
                            + seance.getDate() + " — vérifiez la séance choisie."));
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
    public Map<String, Object> confirmer(ConfirmerImportRpeRequest request) {
        UUID seanceId = UUID.fromString(request.getSeanceId());
        Seance seance = seanceRepository.findById(seanceId)
                .orElseThrow(() -> new IllegalArgumentException("Séance introuvable : " + seanceId));
        scopeResolver.verifieAcces(seance.getEquipeId());
        UUID clubId = clubDeSeance(seance);
        Short dureeSeance = seance.getDureeMinutes();

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
        for (LigneRpeImportDto ligne : request.getLignes()) {
            if (ligne.getRpe() == null) continue; // sans réponse : jamais importée

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

            // Une fiche qui reçoit du RPE doit être dans l'effectif de la saison en cours, sinon
            // elle reste invisible partout (miroir de l'import GPS).
            joueurService.inscrireEffectifSiHorsSaison(joueur, seance.getEquipeId());

            RpeSeance r = rpeRepository.findByJoueurIdAndSeanceId(joueurId, seance.getId())
                    .orElseGet(RpeSeance::new);
            r.setJoueurId(joueurId);
            r.setEquipeId(seance.getEquipeId());
            r.setSeanceId(seance.getId());
            r.setSeanceType(TYPE_PHYSIQUE);
            r.setDate(seance.getDate());
            r.setRpe(ligne.getRpe());
            r.setDureeMinutes(dureeSeance);
            r.setCharge(dureeSeance != null ? ligne.getRpe() * dureeSeance : null);
            r.setPlaisir(ligne.getPlaisir());
            rpeRepository.save(r);
            inseres++;
        }

        // Marquer la séance réalisée si elle a eu lieu (jour J ou passé) — comme l'import GPS.
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

    /* ═════════════════════════ DÉTECTION COLONNES ═════════════════════════ */

    /** Positions des colonnes utiles (-1 = absente). Détection par en-tête, tolérante au mojibake. */
    private record Colonnes(int identite, int rpe, int plaisir, int date) {}

    private Colonnes detecteColonnes(List<String> entetes) {
        int identite = -1, rpe = -1, rpeFallback = -1, plaisir = -1, date = -1;
        for (int i = 0; i < entetes.size(); i++) {
            String n = normalise(entetes.get(i));
            if (identite < 0 && n.contains("nom") && n.contains("joueur")) identite = i;
            if (rpe < 0 && n.equals("rpe")) rpe = i;
            if (rpeFallback < 0 && n.contains("rpe") && !n.contains("individuel")) rpeFallback = i;
            if (plaisir < 0 && n.contains("plaisir")) plaisir = i;
            if (date < 0 && n.contains("date")) date = i; // 1ère colonne « date » = date de séance
        }
        return new Colonnes(identite, rpe >= 0 ? rpe : rpeFallback, plaisir, date);
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

    /** Note 1..10 ; null si vide. Hors plage → avertissement + null (non importée). */
    private Short litNote(String brut, int numeroLigne, String qui, String colonne,
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

    private UUID clubDeSeance(Seance seance) {
        if (seance.getEquipeId() == null) {
            throw new IllegalArgumentException("Séance sans équipe : import impossible");
        }
        return equipeRepository.findById(seance.getEquipeId())
                .orElseThrow(() -> new IllegalArgumentException("Équipe introuvable"))
                .getClubId();
    }
}
