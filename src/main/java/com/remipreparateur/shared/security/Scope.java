package com.remipreparateur.shared.security;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Portee de visibilite des donnees pour l'utilisateur courant.
 * - all       : aucune restriction (super-admin)
 * - equipeIds : limite a ces equipes (president = equipes de son club ; staff/joueur = la sienne)
 * - none()    : aucun acces (all=false et equipeIds vide)
 *
 * <p><b>Fenêtre de saison</b> ({@code debut}/{@code fin}, V105) : borne temporelle de la saison
 * consultée. Elle s'applique aux LISTES — ce qu'on affiche — et jamais aux MOTEURS de calcul.
 * L'ACWR, la charge chronique, la fatigue et les dérives raisonnent sur des fenêtres glissantes
 * (4 semaines) qui doivent pouvoir franchir une frontière de saison : les borner ferait repartir
 * de zéro chaque début de saison et chaque reprise de janvier. Le hors-saison est déjà traité en
 * amont, côté Python, par le contexte de période.</p>
 *
 * <p>Les deux bornes sont nulles quand le client n'envoie pas {@code X-Contexte-Saison} : le
 * comportement est alors exactement celui d'avant.</p>
 */
public record Scope(boolean all, List<UUID> equipeIds, LocalDate debut, LocalDate fin) {

    public Scope(boolean all, List<UUID> equipeIds) {
        this(all, equipeIds, null, null);
    }

    public boolean none() {
        return !all && equipeIds.isEmpty();
    }

    /** Une saison est-elle ciblée ? (sinon les listes ne posent aucune borne de date) */
    public boolean borne() {
        return debut != null && fin != null;
    }

    /** Même portée d'équipes, bornée à une fenêtre de dates. */
    public Scope dans(LocalDate d, LocalDate f) {
        return new Scope(all, equipeIds, d, f);
    }

    /**
     * Intersection de la fenêtre de saison avec une fenêtre demandée par l'appelant.
     * Un écran qui réclame déjà des dates (calendrier, import) garde la main : on ne fait que
     * resserrer, jamais élargir.
     */
    public LocalDate debutEffectif(LocalDate demande) {
        if (debut == null) return demande;
        if (demande == null) return debut;
        return demande.isBefore(debut) ? debut : demande;
    }

    public LocalDate finEffective(LocalDate demande) {
        if (fin == null) return demande;
        if (demande == null) return fin;
        return demande.isAfter(fin) ? fin : demande;
    }

    public static Scope tout() {
        return new Scope(true, List.of());
    }

    public static Scope equipes(List<UUID> ids) {
        return new Scope(false, ids);
    }

    public static Scope aucun() {
        return new Scope(false, List.of());
    }
}
