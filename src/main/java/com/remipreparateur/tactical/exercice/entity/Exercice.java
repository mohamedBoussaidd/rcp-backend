package com.remipreparateur.tactical.exercice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "exercice")
@Getter
@Setter
public class Exercice {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    /** Club propriétaire, ou {@code null} pour un exercice GLOBAL (bibliothèque super-admin, CB). */
    @Column(name = "club_id")
    private UUID clubId;

    @Column(name = "nom", nullable = false)
    private String nom;

    /**
     * Forme de travail : ECHAUFFEMENT / ANALYTIQUE / SITUATION / JEU_REDUIT / MATCH_A_THEME.
     * V65 : remplace l'ancienne {@code categorie}, qui mélangeait la forme, le moment de séance
     * et le thème de jeu — ce dernier vit désormais dans {@link #sousPrincipeIds}.
     */
    @Column(name = "forme", length = 20)
    private String forme;

    /**
     * Thèmes de jeu travaillés, pris dans le MÊME référentiel que la séance
     * ({@code referentiel_sous_principe}) : un seul vocabulaire dans toute l'application.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "exercice_sous_principe", joinColumns = @JoinColumn(name = "exercice_id"))
    @Column(name = "sous_principe_id")
    private java.util.List<UUID> sousPrincipeIds = new java.util.ArrayList<>();

    // PHYSIQUE / TECHNIQUE / MIXTE : oriente le contenu et l'usage des attentes physiques.
    @Column(name = "type", nullable = false, length = 20)
    private String type = "TECHNIQUE";

    @Column(name = "duree_minutes")
    private Short dureeMinutes;

    @Column(name = "objectif")
    private String objectif;

    @Column(name = "intensite")
    private Short intensite;

    // ── Attentes physiques (optionnelles, surtout pour les exercices PHYSIQUE) ──
    @Column(name = "distance_attendue_m")
    private Integer distanceAttendueM;

    @Column(name = "distance_haute_intensite_m")
    private Integer distanceHauteIntensiteM;

    @Column(name = "nb_sprints")
    private Short nbSprints;

    @Column(name = "description")
    private String description;

    // ── Mode avancé (module seance_avancee) : cadre pédagogique — tout optionnel ──
    // NB (V65) : le « contexte » a quitté l'exercice pour la séance — il décrit un moment précis
    // (« séance à J+2 »), pas une propriété d'un objet réutilisé dans 50 séances.

    // TEMPS_DE_JEU / PRINCIPE_ACTION / REGLE_ACTION_COLLECTIVE / REGLE_ACTION_INDIVIDUELLE / MOYEN
    @Column(name = "niveau_objectif", length = 40)
    private String niveauObjectif;

    // COLLECTIF / INTERSECTORIEL / SECTORIEL / GROUPAL / INDIVIDUEL
    @Column(name = "echelle_effectif", length = 20)
    private String echelleEffectif;

    // Les cinq axes de dominante. V68 : chacun porte un DOSAGE 0-5, et le texte historique
    // n'est plus que la note facultative qui le précise. Deux exercices deviennent ainsi
    // comparables, et une séance peut agréger les dominantes de ses exercices — ce que du
    // texte libre ne permettait pas.

    @Column(name = "dominante_tactique_org_intensite")
    private Short dominanteTactiqueOrgIntensite;

    @Column(name = "dominante_tactique_org")
    private String dominanteTactiqueOrg;

    @Column(name = "dominante_tactique_fonc_intensite")
    private Short dominanteTactiqueFoncIntensite;

    @Column(name = "dominante_tactique_fonc")
    private String dominanteTactiqueFonc;

    @Column(name = "dominante_mental_intensite")
    private Short dominanteMentalIntensite;

    @Column(name = "dominante_mental")
    private String dominanteMental;

    @Column(name = "dominante_technique_intensite")
    private Short dominanteTechniqueIntensite;

    @Column(name = "dominante_technique")
    private String dominanteTechnique;

    @Column(name = "dominante_athletique_intensite")
    private Short dominanteAthletiqueIntensite;

    @Column(name = "dominante_athletique")
    private String dominanteAthletique;

    /** Règles du jeu ET système de marque (fusionnés en V65 : « 1 but = 1 pt » est une règle). */
    @Column(name = "regles_jeu")
    private String reglesJeu;

    @Column(name = "variables_pedagogiques")
    private String variablesPedagogiques;

    @Column(name = "reperes_perceptifs")
    private String reperesPerceptifs;

    @Column(name = "comportements_attendus")
    private String comportementsAttendus;

    // ── Mode avancé : organisation (densité m²/joueur calculée côté front, jamais stockée) ──
    @Column(name = "terrain_longueur_m", precision = 5, scale = 1)
    private java.math.BigDecimal terrainLongueurM;

    @Column(name = "terrain_largeur_m", precision = 5, scale = 1)
    private java.math.BigDecimal terrainLargeurM;

    @Column(name = "format_joueurs", length = 120)
    private String formatJoueurs;

    /** Pré-rempli côté front depuis {@code formatJoueurs}, mais restant corrigeable à la main. */
    @Column(name = "nb_joueurs_total")
    private Short nbJoueursTotal;

    // NB (V65) : le séquençage a quitté l'exercice pour le BLOC de séance. Il existait aux deux
    // endroits sans règle de priorité — c'est un paramètre d'exécution du jour, pas de bibliothèque.

    @Column(name = "schema_json", columnDefinition = "text")
    private String schemaJson;

    /** Import photo d'origine (pièce jointe consultable), nullable. */
    @Column(name = "photo_import_id")
    private UUID photoImportId;

    @Column(name = "cree_par")
    private UUID creePar;

    @Column(name = "equipe_origine_id")
    private UUID equipeOrigineId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
