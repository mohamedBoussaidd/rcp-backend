package com.remipreparateur.performance.importation.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * Une ligne de l'export « playermonitoring » (ressenti quotidien / Hooper), déjà CONVERTIE
 * à la convention de l'app et appariée à une fiche. Les valeurs portées ici sont celles qui
 * seront écrites en base : items 1..10 où 1 = bon → 10 = mauvais (inversion faite au parsing),
 * {@code stress} par défaut (non demandé dans l'export), gêne localisée optionnelle.
 *
 * <p>Une ligne dont les 4 items de ressenti sont absents ({@code repondu = false}) est affichée
 * pour information (le joueur n'a pas répondu) mais n'est jamais importée.
 */
@Data
public class LigneHooperImportDto {
    /** Numéro de la ligne dans le fichier d'origine (1-indexé). */
    private Integer numeroLigne;
    /** Identité telle que lue dans le fichier (« Nom Prénom »). */
    private String identiteFichier;
    private String joueurId;
    /** Nom d'affichage de la fiche appariée ; null si joueur inconnu. */
    private String joueurNomAffiche;
    /** Date du ressenti (issue de « Date de la séance », PAS de la date de réponse). */
    private LocalDate date;

    // ── Items Hooper, déjà convertis (1 = bon → 10 = mauvais) ──
    private Short sommeil;
    private Short fatigue;
    private Short douleur;
    private Short stress;
    private Short humeur;

    // ── Gêne localisée optionnelle (Emplacement + Douleur de l'export) ──
    private String geneZone;
    private Short geneIntensite;

    /**
     * false = ligne sans ressenti (non importée). Nommé {@code repondu} et NON {@code aRepondu} à
     * dessein : un booléen {@code aXxx} produit le getter Lombok {@code isAXxx()} que Jackson
     * sérialise sous une clé démanglée (« axxx ») différente du nom de champ — le front lirait
     * alors {@code undefined} et griserait toutes les lignes. {@code isRepondu()} donne une clé
     * JSON unique et propre « repondu ». Verrouillé par un test (cf. import RPE).
     */
    private boolean repondu;
}
