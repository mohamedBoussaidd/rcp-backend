package com.remipreparateur.performance.objectifperiode.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Contrats de l'arbitrage d'une semaine à plusieurs matchs. */
public class ArbitrageDtos {

    /** Un delta porté par une semaine, tel qu'affiché sous le Retenu. */
    public record ReportDto(LocalDate dateLundiCible, String metrique, int delta) {}

    /**
     * État complet d'une semaine du point de vue « double match » : ce que dit le calendrier,
     * ce qui a été décidé, et ce que la décision a produit. Un seul appel pour peindre la modale.
     */
    public record SemaineArbitrageDto(
            UUID equipeId,
            LocalDate dateLundi,
            int nbMatchs,
            List<LocalDate> datesMatchs,
            /** ALLEGER | ASSUMER | RELISSER, ou null si rien n'a encore été décidé. */
            String choix,
            String note,
            /** Fin de la période de saison : le report ne la franchit jamais. */
            LocalDate periodeFin,
            List<ReportDto> reports,
            /** Semaines qui peuvent recevoir un report (2 au plus, dans la même période). */
            List<LocalDate> semainesCibles,
            boolean referentielAdopte,
            /** Distance d'un match selon le référentiel, en mètres — l'ampleur d'un report. */
            Integer matchDistanceM,
            String avertissement) {}

    /** L'équipe n'est pas dans la requête : elle vient du contexte actif, comme l'objectif hebdo. */
    public record ArbitrageRequest(LocalDate dateLundi, String choix, String note) {}
}
