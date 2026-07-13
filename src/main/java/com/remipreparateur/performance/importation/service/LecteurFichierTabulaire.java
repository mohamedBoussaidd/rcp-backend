package com.remipreparateur.performance.importation.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Lit un fichier tabulaire (xlsx, CSV ou texte collé) vers une structure unique : en-têtes +
 * lignes de cellules String. Détecte la ligne d'en-tête, le séparateur et l'encodage CSV, la
 * virgule décimale. Les lignes entièrement vides sont ignorées ; les lignes TOTAL/MOYENNE sans
 * identité sont conservées ici (filtrées plus tard, une fois la colonne identité connue).
 */
@Component
public class LecteurFichierTabulaire {

    /** Résultat de lecture : en-têtes bruts + lignes de données avec leur numéro d'origine. */
    public record Tableau(List<String> entetes, List<LigneBrute> lignes, boolean entetesGenerees) {}

    public record LigneBrute(int numero, List<String> cellules) {}

    private static final DateTimeFormatter ISO_LOCAL = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public Tableau lire(byte[] contenu, String nomFichier) {
        if (estXlsx(contenu, nomFichier)) return lireXlsx(contenu);
        return lireTexte(decoder(contenu));
    }

    public Tableau lireTexteColle(String texte) {
        return lireTexte(texte);
    }

    /* ── xlsx ── */

    private boolean estXlsx(byte[] contenu, String nomFichier) {
        if (nomFichier != null && nomFichier.toLowerCase().endsWith(".xlsx")) return true;
        // Signature ZIP « PK » : les .xlsx sont des archives, les CSV jamais.
        return contenu.length > 1 && contenu[0] == 0x50 && contenu[1] == 0x4B;
    }

    private Tableau lireXlsx(byte[] contenu) {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(contenu))) {
            Sheet sheet = workbook.getSheetAt(0);
            List<List<String>> brut = new ArrayList<>();
            int maxCol = 0;
            for (Row row : sheet) {
                List<String> cellules = new ArrayList<>();
                short dernier = row.getLastCellNum();
                for (int c = 0; c < dernier; c++) {
                    cellules.add(celluleEnTexte(row.getCell(c)));
                }
                brut.add(cellules);
                maxCol = Math.max(maxCol, cellules.size());
            }
            return construire(brut, maxCol);
        } catch (Exception e) {
            throw new IllegalArgumentException("Fichier Excel illisible : " + e.getMessage());
        }
    }

    private String celluleEnTexte(Cell cell) {
        if (cell == null) return "";
        CellType type = cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().format(ISO_LOCAL)
                    : BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    /* ── CSV / texte collé ── */

    private String decoder(byte[] contenu) {
        // BOM UTF-8 explicite, sinon tentative UTF-8 stricte, sinon Windows-1252 (exports FR).
        if (contenu.length >= 3 && (contenu[0] & 0xFF) == 0xEF && (contenu[1] & 0xFF) == 0xBB && (contenu[2] & 0xFF) == 0xBF) {
            return new String(contenu, 3, contenu.length - 3, StandardCharsets.UTF_8);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(contenu)).toString();
        } catch (CharacterCodingException e) {
            return new String(contenu, Charset.forName("windows-1252"));
        }
    }

    private Tableau lireTexte(String texte) {
        List<String> lignesTexte = texte.lines().filter(l -> !l.isBlank()).toList();
        if (lignesTexte.isEmpty()) throw new IllegalArgumentException("Contenu vide");
        char separateur = detecteSeparateur(lignesTexte.get(0));
        List<List<String>> brut = new ArrayList<>();
        int maxCol = 0;
        for (String ligne : lignesTexte) {
            List<String> cellules = decoupe(ligne, separateur);
            brut.add(cellules);
            maxCol = Math.max(maxCol, cellules.size());
        }
        return construire(brut, maxCol);
    }

    private char detecteSeparateur(String premiereLigne) {
        int pv = compteHorsGuillemets(premiereLigne, ';');
        int tab = compteHorsGuillemets(premiereLigne, '\t');
        int virg = compteHorsGuillemets(premiereLigne, ',');
        if (pv >= tab && pv >= virg && pv > 0) return ';';
        if (tab >= virg && tab > 0) return '\t';
        if (virg > 0) return ',';
        return ';';
    }

    private int compteHorsGuillemets(String s, char c) {
        int n = 0;
        boolean dansGuillemets = false;
        for (char ch : s.toCharArray()) {
            if (ch == '"') dansGuillemets = !dansGuillemets;
            else if (ch == c && !dansGuillemets) n++;
        }
        return n;
    }

    private List<String> decoupe(String ligne, char separateur) {
        List<String> cellules = new ArrayList<>();
        StringBuilder courant = new StringBuilder();
        boolean dansGuillemets = false;
        for (int i = 0; i < ligne.length(); i++) {
            char ch = ligne.charAt(i);
            if (ch == '"') {
                if (dansGuillemets && i + 1 < ligne.length() && ligne.charAt(i + 1) == '"') {
                    courant.append('"'); i++;
                } else {
                    dansGuillemets = !dansGuillemets;
                }
            } else if (ch == separateur && !dansGuillemets) {
                cellules.add(courant.toString().trim());
                courant.setLength(0);
            } else {
                courant.append(ch);
            }
        }
        cellules.add(courant.toString().trim());
        return cellules;
    }

    /* ── Détection de la ligne d'en-tête ── */

    private Tableau construire(List<List<String>> brut, int maxCol) {
        int indexEntete = detecteLigneEntete(brut);
        List<String> entetes;
        boolean generees = indexEntete < 0;
        if (generees) {
            entetes = new ArrayList<>();
            for (int c = 0; c < maxCol; c++) entetes.add("Colonne " + (c + 1));
            indexEntete = -1;
        } else {
            entetes = new ArrayList<>(brut.get(indexEntete));
            while (entetes.size() < maxCol) entetes.add("Colonne " + (entetes.size() + 1));
        }
        List<LigneBrute> lignes = new ArrayList<>();
        for (int i = indexEntete + 1; i < brut.size(); i++) {
            List<String> cellules = new ArrayList<>(brut.get(i));
            while (cellules.size() < maxCol) cellules.add("");
            if (cellules.stream().allMatch(String::isEmpty)) continue;
            lignes.add(new LigneBrute(i + 1, cellules)); // numéro 1-indexé tel que vu dans le fichier
        }
        if (lignes.isEmpty()) throw new IllegalArgumentException("Aucune ligne de données trouvée");
        return new Tableau(entetes, lignes, generees);
    }

    /**
     * Première ligne (parmi les 10 premières) dont au moins 3 cellules sont non vides et dont la
     * majorité est non numérique → en-tête. -1 si le fichier commence directement par les données.
     */
    private int detecteLigneEntete(List<List<String>> brut) {
        for (int i = 0; i < Math.min(brut.size(), 10); i++) {
            List<String> cellules = brut.get(i);
            long nonVides = cellules.stream().filter(c -> !c.isEmpty()).count();
            if (nonVides < 3) continue;
            long textes = cellules.stream()
                    .filter(c -> !c.isEmpty())
                    .filter(c -> ImportNormalisation.parseNombre(c) == null)
                    .count();
            if (textes * 2 > nonVides) return i;
            return -1; // première ligne dense rencontrée = numérique → pas d'en-tête
        }
        return -1;
    }
}
