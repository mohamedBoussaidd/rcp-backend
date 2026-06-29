package com.remipreparateur.performance.importation.controller;

import com.remipreparateur.performance.importation.dto.ConfirmerImportRequest;
import com.remipreparateur.performance.importation.dto.LigneGpsImportDto;
import com.remipreparateur.performance.importation.dto.ResolutionImportDto;
import com.remipreparateur.performance.gps.entity.DonneeGps;
import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.performance.seance.entity.Seance;
import com.remipreparateur.performance.seance.entity.TypeSeance;
import com.remipreparateur.performance.gps.repository.DonneeGpsRepository;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.performance.seance.repository.SeanceRepository;
import com.remipreparateur.performance.seance.repository.TypeSeanceRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/import")
@RequiredArgsConstructor
public class ImportController {

    private final TypeSeanceRepository typeSeanceRepository;
    private final SeanceRepository seanceRepository;
    private final JoueurRepository joueurRepository;
    private final DonneeGpsRepository donneeGpsRepository;

    /* ─────────────────────────────────────────────────────────
       ÉTAPE 1 : Analyser le fichier, identifier les inconnus
       ───────────────────────────────────────────────────────── */
    @PostMapping("/excel/analyser")
    public ResponseEntity<?> analyserExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("seanceId") UUID seanceId) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Fichier vide"));
        }

        try {
            seanceRepository.findById(seanceId)
                    .orElseThrow(() -> new IllegalArgumentException("Séance introuvable : " + seanceId));

            Workbook workbook = new XSSFWorkbook(file.getInputStream());
            Sheet sheet = workbook.getSheetAt(0);

            List<LigneGpsImportDto> lignes = new ArrayList<>();
            Set<String> inconnus = new LinkedHashSet<>();

            for (Row row : sheet) {
                Cell nomCell = row.getCell(0);
                Cell numCell = row.getCell(1);
                if (nomCell == null || numCell == null) continue;
                if (nomCell.getCellType() != CellType.STRING) continue;
                if (numCell.getCellType() != CellType.NUMERIC) continue;

                String prenom = nomCell.getStringCellValue().trim();
                if (prenom.isEmpty() || prenom.equalsIgnoreCase("MOYENNE")) continue;

                Optional<Joueur> joueurOpt = joueurRepository.findByPrenomIgnoreCase(prenom);

                LigneGpsImportDto ligne = new LigneGpsImportDto();
                ligne.setPrenomFichier(prenom);
                ligne.setJoueurId(joueurOpt.map(j -> j.getId().toString()).orElse(null));
                ligne.setDureeMinutes(toShort(row, 2));
                ligne.setDistanceTotaleM(toDecimal(row, 3));
                ligne.setDistance15kmhM(toDecimal(row, 4));
                ligne.setDistance19kmhM(toDecimal(row, 5));
                ligne.setDistanceSprint24kmhM(toDecimal(row, 6));
                ligne.setDistanceSprint28kmhM(toDecimal(row, 7));
                ligne.setNbSprints24kmh(toShort(row, 8));
                ligne.setVitesseMaxKmh(toDecimal(row, 9));
                ligne.setNbAccelerations(toShort(row, 10));
                ligne.setNbFreinages(toShort(row, 11));
                ligne.setRatioDistanceMin(toDecimal(row, 12));

                lignes.add(ligne);
                if (joueurOpt.isEmpty()) {
                    inconnus.add(prenom);
                }
            }
            workbook.close();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("seanceId", seanceId.toString());
            result.put("lignes", lignes);
            result.put("joueursInconnus", new ArrayList<>(inconnus));
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /* ─────────────────────────────────────────────────────────
       ÉTAPE 2 : Confirmer avec les résolutions
       ───────────────────────────────────────────────────────── */
    @PostMapping("/excel/confirmer")
    public ResponseEntity<?> confirmerImport(@RequestBody ConfirmerImportRequest request) {
        try {
            UUID seanceId = UUID.fromString(request.getSeanceId());
            Seance seance = seanceRepository.findById(seanceId)
                    .orElseThrow(() -> new IllegalArgumentException("Séance introuvable : " + seanceId));

            // Construire la map prenomFichier -> joueurId depuis les résolutions
            Map<String, UUID> resolutionMap = new HashMap<>();
            for (ResolutionImportDto res : request.getResolutions()) {
                switch (res.getAction()) {
                    case "IGNORE" -> { /* skip */ }
                    case "MERGE" -> resolutionMap.put(
                            res.getPrenomFichier(),
                            UUID.fromString(res.getJoueurExistantId()));
                    case "CREATE" -> {
                        Joueur j = new Joueur();
                        j.setPrenom(res.getPrenom() != null ? res.getPrenom() : res.getPrenomFichier());
                        j.setNom(res.getNom());
                        j.setPostePrincipal(res.getPoste());
                        j.setStatut("actif");
                        j = joueurRepository.save(j);
                        resolutionMap.put(res.getPrenomFichier(), j.getId());
                    }
                }
            }

            // Importer les données GPS
            int inseres = 0;
            int ignores = 0;

            for (LigneGpsImportDto ligne : request.getLignes()) {
                UUID joueurId;
                if (ligne.getJoueurId() != null) {
                    joueurId = UUID.fromString(ligne.getJoueurId());
                } else if (resolutionMap.containsKey(ligne.getPrenomFichier())) {
                    joueurId = resolutionMap.get(ligne.getPrenomFichier());
                } else {
                    ignores++;
                    continue;
                }

                final UUID finalJoueurId = joueurId;
                Joueur joueur = joueurRepository.findById(joueurId).orElse(null);
                if (joueur == null) continue;

                final Joueur finalJoueur = joueur;
                DonneeGps gps = donneeGpsRepository
                        .findByJoueurIdAndSeanceId(finalJoueurId, seance.getId())
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
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /* ─────────────────────────────────────────────────────────
       Ancien endpoint — conservé pour compatibilité
       ───────────────────────────────────────────────────────── */
    @PostMapping("/excel")
    public ResponseEntity<?> importExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "seanceId", required = false) UUID seanceId,
            @RequestParam(value = "date", required = false) String dateStr,
            @RequestParam(value = "typeSeance", required = false) String typeSeanceCode) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Fichier vide"));
        }

        try {
            Seance seanceTrouvee;
            if (seanceId != null) {
                seanceTrouvee = seanceRepository.findById(seanceId)
                        .orElseThrow(() -> new IllegalArgumentException("Séance introuvable : " + seanceId));
            } else if (dateStr != null && typeSeanceCode != null) {
                LocalDate date = LocalDate.parse(dateStr);
                TypeSeance typeSeance = typeSeanceRepository.findByCode(typeSeanceCode)
                        .orElseThrow(() -> new IllegalArgumentException("Type de séance inconnu : " + typeSeanceCode));
                seanceTrouvee = seanceRepository.findByDateAndTypeSeanceId(date, typeSeance.getId())
                        .orElseGet(() -> {
                            Seance s = new Seance();
                            s.setDate(date);
                            s.setTypeSeance(typeSeance);
                            if (!date.isAfter(LocalDate.now())) s.setStatut("REALISEE");
                            return s;
                        });
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "Paramètre seanceId ou date+typeSeance requis"));
            }

            if (!"REALISEE".equals(seanceTrouvee.getStatut())
                    && seanceTrouvee.getDate() != null && !seanceTrouvee.getDate().isAfter(LocalDate.now())) {
                seanceTrouvee.setStatut("REALISEE");
            }
            final Seance seance = seanceRepository.save(seanceTrouvee);

            Workbook workbook = new XSSFWorkbook(file.getInputStream());
            Sheet sheet = workbook.getSheetAt(0);
            int inseres = 0;
            List<String> nonTrouves = new ArrayList<>();

            for (Row row : sheet) {
                Cell nomCell = row.getCell(0);
                Cell numCell = row.getCell(1);
                if (nomCell == null || numCell == null) continue;
                if (nomCell.getCellType() != CellType.STRING) continue;
                if (numCell.getCellType() != CellType.NUMERIC) continue;
                String prenom = nomCell.getStringCellValue().trim();
                if (prenom.isEmpty() || prenom.equalsIgnoreCase("MOYENNE")) continue;

                Optional<Joueur> joueurOpt = joueurRepository.findByPrenomIgnoreCase(prenom);
                if (joueurOpt.isEmpty()) { nonTrouves.add(prenom); continue; }
                Joueur joueur = joueurOpt.get();

                DonneeGps gps = donneeGpsRepository.findByJoueurIdAndSeanceId(joueur.getId(), seance.getId())
                        .orElseGet(() -> { DonneeGps g = new DonneeGps(); g.setJoueur(joueur); g.setSeance(seance); return g; });

                gps.setDureeMinutes(toShort(row, 2));
                gps.setDistanceTotaleM(toDecimal(row, 3));
                gps.setDistance15kmhM(toDecimal(row, 4));
                gps.setDistance19kmhM(toDecimal(row, 5));
                gps.setDistanceSprint24kmhM(toDecimal(row, 6));
                gps.setDistanceSprint28kmhM(toDecimal(row, 7));
                gps.setNbSprints24kmh(toShort(row, 8));
                gps.setVitesseMaxKmh(toDecimal(row, 9));
                gps.setNbAccelerations(toShort(row, 10));
                gps.setNbFreinages(toShort(row, 11));
                gps.setRatioDistanceMin(toDecimal(row, 12));
                donneeGpsRepository.save(gps);
                inseres++;
            }
            workbook.close();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("seanceId", seance.getId());
            result.put("date", seance.getDate().toString());
            result.put("typeSeance", seance.getTypeSeance().getCode());
            result.put("inseres", inseres);
            result.put("nonTrouves", nonTrouves);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private Short toShort(Row row, int col) {
        Cell c = row.getCell(col);
        if (c == null || c.getCellType() != CellType.NUMERIC) return null;
        return (short) c.getNumericCellValue();
    }

    private BigDecimal toDecimal(Row row, int col) {
        Cell c = row.getCell(col);
        if (c == null || c.getCellType() != CellType.NUMERIC) return null;
        return BigDecimal.valueOf(c.getNumericCellValue()).setScale(2, RoundingMode.HALF_UP);
    }
}
