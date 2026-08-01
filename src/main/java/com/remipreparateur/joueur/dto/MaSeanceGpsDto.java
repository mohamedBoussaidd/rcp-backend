package com.remipreparateur.joueur.dto;

import com.remipreparateur.performance.gps.dto.GpsHistoriqueDto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Une séance passée du point de vue du JOUEUR : ce qu'il a fait, et — s'il portait un capteur —
 * ce qui a été mesuré.
 *
 * <p>Le GPS seul ne suffisait pas : une séance sans capteur disparaissait de son historique, ce
 * qui se lit comme un oubli de l'application. En portant le statut d'appel à côté de la mesure,
 * l'absence de chiffres s'explique d'elle-même (« au soin », « absent », « pas de capteur »).</p>
 *
 * <p>{@code gps} est {@code null} quand aucune donnée n'a été mesurée. Les repères personnels
 * (moyenne par type de séance, record de vitesse) ne sont volontairement PAS calculés ici : le
 * front reçoit tout l'historique et les dérive sans requête supplémentaire.</p>
 */
public record MaSeanceGpsDto(
        UUID seanceId,
        LocalDate date,
        String typeCode,
        String typeLibelle,
        /** PRESENT / RETARD / ADAPTE / SOIN / EXCUSE / ABSENT — présence par exception : sans
         *  déclaration, le joueur est réputé présent. */
        String statutPresence,
        GpsHistoriqueDto gps
) {}
