package com.remipreparateur.performance.referentiel.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * En-tête d'un RÉFÉRENTIEL de charge : « qu'est-ce qui est normal pour ce poste, à ce niveau ? »
 *
 * <p>Une NORME, jamais un objectif. Le référentiel ne connaît ni la saison, ni les équipes, ni les
 * périodes : c'est un dictionnaire, fourni. L'objectif — ce que CE club décide pour CETTE période —
 * est un autre objet, qui vient s'appuyer dessus.
 *
 * <p>Deux origines, distinguées par {@code clubId} :
 * <ul>
 *   <li>{@code clubId == null} — publié par le super-admin, visible de tous, jamais modifiable
 *       par un club (mêmes mœurs que les schémas globaux et le catalogue des types de séance) ;</li>
 *   <li>{@code clubId != null} — copie d'un référentiel plateforme, personnalisée par le club.
 *       {@code sourceId} garde le lien d'origine pour pouvoir montrer l'écart.</li>
 * </ul>
 *
 * <p><b>Un référentiel PUBLIÉ est immuable.</b> Corriger une valeur crée une NOUVELLE version
 * ({@code parentId} + {@code version}), et les clubs restent épinglés sur la leur jusqu'à ce
 * qu'ils migrent. Sans cette règle, une retouche du super-admin ferait passer un joueur de vert à
 * rouge dans tous les clubs, du jour au lendemain, sans que personne n'ait rien fait — l'incident
 * est déjà documenté sur le catalogue des types de séance
 * ({@code TypeSeanceCible}, « une couleur éditable au niveau du type aurait repeint le calendrier
 * de tous les clubs de la plateforme »).
 */
@Entity
@Table(name = "referentiel_objectif")
@Getter
@Setter
public class ReferentielObjectif {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    /** {@code null} = référentiel plateforme publié par le super-admin. */
    @Column(name = "club_id")
    private UUID clubId;

    /** Nom parlant, c'est lui que le préparateur cite : « National N1 — 2026/27 ». */
    @Column(name = "nom", nullable = false)
    private String nom;

    /** Niveau de compétition, pour regrouper les versions successives : N1, N2, R1, U19… */
    @Column(name = "niveau")
    private String niveau;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    /** Version précédente de CE référentiel (chaîne de versions). */
    @Column(name = "parent_id")
    private UUID parentId;

    /** Référentiel plateforme dont ce référentiel de club est parti (pour l'écart à la source). */
    @Column(name = "source_id")
    private UUID sourceId;

    /** BROUILLON | PUBLIE | ARCHIVE (contrainte CHECK en base). Seul un BROUILLON est modifiable. */
    @Column(name = "statut", nullable = false)
    private String statut = STATUT_BROUILLON;

    @Column(name = "cree_par")
    private UUID creePar;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public static final String STATUT_BROUILLON = "BROUILLON";
    public static final String STATUT_PUBLIE    = "PUBLIE";
    public static final String STATUT_ARCHIVE   = "ARCHIVE";

    /** Un référentiel plateforme : publié par le super-admin, adoptable par n'importe quel club. */
    public boolean estPlateforme() { return clubId == null; }

    /** Seul un brouillon accepte une écriture de valeur. */
    public boolean estModifiable() { return STATUT_BROUILLON.equals(statut); }
}
