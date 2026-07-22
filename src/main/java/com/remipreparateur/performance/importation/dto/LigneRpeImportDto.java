package com.remipreparateur.performance.importation.dto;

import lombok.Data;

/**
 * Une ligne du fichier RPE post-séance, convertie et appariée à une fiche.
 * Une ligne sans RPE ({@code repondu = false}) est affichée pour information
 * (le joueur n'a pas répondu) mais n'est jamais importée.
 */
@Data
public class LigneRpeImportDto {
    /** Numéro de la ligne dans le fichier d'origine (1-indexé). */
    private Integer numeroLigne;
    /** Identité telle que lue dans le fichier (« Nom Prénom »). */
    private String identiteFichier;
    private String joueurId;
    /** Nom d'affichage de la fiche appariée ; null si joueur inconnu. */
    private String joueurNomAffiche;
    /** RPE 1..10 (null si le joueur n'a pas répondu). */
    private Short rpe;
    /** Plaisir 1..10 (null si absent). */
    private Short plaisir;
    /** Durée héritée de la séance cible (minutes). */
    private Short dureeMinutes;
    /** Charge sRPE = rpe × durée (aperçu ; null si l'un manque). */
    private Integer charge;
    /**
     * false = ligne sans RPE (non importée). Nommé {@code repondu} et NON {@code aRepondu} à
     * dessein : un booléen {@code aXxx} produit le getter Lombok {@code isAXxx()} que Jackson
     * sérialise sous une clé démanglée (« axxx ») différente du nom de champ — le front lisait
     * alors {@code undefined} et grisait toutes les lignes (« Aucune ligne à importer »).
     * {@code isRepondu()} donne une clé JSON unique et propre « repondu ». Verrouillé par un test.
     */
    private boolean repondu;
}
