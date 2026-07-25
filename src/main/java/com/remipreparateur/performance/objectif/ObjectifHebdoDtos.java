package com.remipreparateur.performance.objectif;

import java.util.UUID;

/** Contrats d'entrée/sortie de l'objectif hebdomadaire de charge. */
public final class ObjectifHebdoDtos {

    /** Corps du PUT : {@code objectifDistanceM} null = efface l'objectif (retour à la suggestion). */
    public record MajRequest(Integer objectifDistanceM) {}

    public record Reponse(UUID equipeId, Integer objectifDistanceM) {}

    private ObjectifHebdoDtos() {}
}
