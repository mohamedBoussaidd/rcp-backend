package com.remipreparateur.performance.importation.dto;

/**
 * Métriques cibles d'une colonne de fichier GPS. Les zones de distance restent les 4 colonnes
 * cumulatives de donnee_gps (>15 / >19 / >24 / >28 km/h) : un fichier aux seuils différents est
 * mappé sur la zone la plus proche, le seuil réel étant conservé dans le profil (libellés
 * dynamiques). La sémantique BANDE (distance sur une plage, ex. 24-28) est re-cumulée à la
 * conversion.
 */
public enum MetriqueImport {
    IDENTITE,           // colonne portant le nom du joueur
    DATE_SEANCE,        // date d'activité (contrôle de cohérence avec la séance choisie)
    DUREE,              // minutes, secondes ou hh:mm:ss selon formatDuree
    DISTANCE_TOTALE,
    DISTANCE_Z15,
    DISTANCE_Z19,
    DISTANCE_Z24,
    DISTANCE_Z28,
    NB_SPRINTS,
    VITESSE_MAX,
    NB_ACCELERATIONS,
    NB_FREINAGES,
    RATIO_DISTANCE_MIN
}
