package com.remipreparateur.notification.entity;

/**
 * Types de notification. {@code categorie} sert au regroupement UI et aux règles
 * (rappels = scheduler, alertes = seuils, messages = émetteur humain). {@code seuil}
 * indique si le type est piloté par un seuil configurable (sinon purement événementiel).
 */
public enum TypeNotification {

    // ── Rappels joueur (programmés) ──
    RAPPEL_WELLNESS(Categorie.RAPPEL, false),
    RAPPEL_RPE(Categorie.RAPPEL, false),
    RAPPEL_POIDS(Categorie.RAPPEL, false),
    RAPPEL_SEANCE(Categorie.RAPPEL, false),

    // ── Rappel staff (programmé) : vérifier la semaine d'entraînement à venir ──
    VERIF_SEMAINE(Categorie.RAPPEL, false),

    // ── Infos joueur (événementiel) ──
    SEANCE_MODIFIEE(Categorie.INFO, false),
    DOC_MEDICAL(Categorie.INFO, false),
    GENE_SUIVI(Categorie.INFO, false),
    MATCH_PARTAGE(Categorie.INFO, false),
    ENTRETIEN_PARTAGE(Categorie.INFO, false),
    ENTRETIEN_PLANIFIE(Categorie.INFO, false),  // RDV d'entretien planifié/déplacé → info joueur
    DOCUMENT_ADMIN_STATUT(Categorie.INFO, false),   // document validé/refusé → info joueur
    DOCUMENT_ADMIN_EXPIRE(Categorie.INFO, false),   // document expiré (job quotidien) → info joueur
    BULLETIN_DISPONIBLE(Categorie.INFO, false),     // fiche de paye distribuée → info à la personne
    CONTRAT_ECHEANCE(Categorie.INFO, false),        // contrat J-90/J-30 → personne + Président/Administratif
    RAPPEL_DOCUMENT_ADMIN(Categorie.RAPPEL, false), // relance hebdo : document manquant/refusé

    // ── Info staff (événementiel) ──
    SEANCE_PARTAGEE(Categorie.INFO, false),         // fiche séance partagée au staff de l'équipe

    // ── Messages humains ──
    MESSAGE_STAFF(Categorie.MESSAGE, false),
    MESSAGE_JOUEUR(Categorie.MESSAGE, false),

    // ── Alertes staff (seuils) ──
    ALERTE_CHARGE(Categorie.ALERTE, true),
    ALERTE_READINESS(Categorie.ALERTE, true),
    ALERTE_WELLNESS(Categorie.ALERTE, true),
    ALERTE_POIDS(Categorie.ALERTE, true),
    ALERTE_COMPLETION(Categorie.ALERTE, true),
    ALERTE_STATUT(Categorie.ALERTE, false),
    ALERTE_ENTRETIEN(Categorie.ALERTE, true),   // digest hebdo : joueurs sans entretien récent (staff)
    ALERTE_CONFORMITE_DOCUMENTS(Categorie.ALERTE, false),  // digest club-wide : conformité documentaire (staff)
    ALERTE_GENE(Categorie.ALERTE, false),   // urgent, unitaire immédiat (médical)
    RETOUR_BLESSURE_A_CONFIRMER(Categorie.ALERTE, false),  // retour soldé auto → staff confirme/prolonge
    DIGEST(Categorie.ALERTE, false),

    // ── Transverses / système ──
    COMPTE(Categorie.SYSTEME, false),
    ECHEANCE(Categorie.SYSTEME, false),
    ANNONCE(Categorie.SYSTEME, false);   // annonce diffusée par le super-admin (broadcast)

    public enum Categorie { RAPPEL, INFO, MESSAGE, ALERTE, SYSTEME }

    private final Categorie categorie;
    private final boolean seuil;

    TypeNotification(Categorie categorie, boolean seuil) {
        this.categorie = categorie;
        this.seuil = seuil;
    }

    public Categorie categorie() { return categorie; }

    /** Le déclenchement de ce type dépend-il d'un seuil configurable ? */
    public boolean piloteParSeuil() { return seuil; }

    /** Type destiné au staff (alertes/digests) ? */
    public boolean pourStaff() { return categorie == Categorie.ALERTE; }
}
