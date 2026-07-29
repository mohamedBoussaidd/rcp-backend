package com.remipreparateur.ia.service;

import com.remipreparateur.tactical.importphoto.entity.ClubParametre;
import com.remipreparateur.tactical.importphoto.repository.ClubParametreRepository;
import com.remipreparateur.tactical.importphoto.service.ParametreIaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Nom affiché de l'assistant conversationnel, résolu en trois niveaux :
 * <ol>
 *   <li>surcharge du club ({@code club_parametre.nom_assistant}) — l'offre « nommez votre assistant »,
 *       saisie par le super-admin pour le compte du club ;</li>
 *   <li>valeur globale ({@code parametre_ia.nom_assistant}) — s'applique à toute l'application ;</li>
 *   <li>défaut en dur ({@code Tempo}).</li>
 * </ol>
 *
 * <p>Aucune migration n'est nécessaire : {@code club_parametre} est déjà la table clé/valeur des
 * surcharges par club (elle porte les quotas IA). Le nom est injecté dans le prompt système du chat
 * à la place du marqueur {@code {nom}}, et renvoyé au front pour l'en-tête du widget.
 */
@Service
public class NomAssistantService {

    private final ParametreIaService parametres;
    private final ClubParametreRepository clubParametres;

    public NomAssistantService(ParametreIaService parametres, ClubParametreRepository clubParametres) {
        this.parametres = parametres;
        this.clubParametres = clubParametres;
    }

    /** Nom pour ce club (null = pas de contexte club → valeur globale). Jamais vide. */
    public String pour(UUID clubId) {
        if (clubId != null) {
            String surcharge = clubParametres.findByClubIdAndCle(clubId, ParametreIaService.CLE_NOM_ASSISTANT)
                    .map(ClubParametre::getValeur).orElse(null);
            if (surcharge != null && !surcharge.isBlank()) return surcharge.trim();
        }
        String global = parametres.valeur(ParametreIaService.CLE_NOM_ASSISTANT);
        return (global == null || global.isBlank()) ? ParametreIaService.NOM_ASSISTANT_DEFAUT : global.trim();
    }

    /** Surcharge (ou efface, si {@code nom} est vide) le nom pour un club. Réservé au super-admin. */
    @Transactional
    public void definirPourClub(UUID clubId, String nom) {
        if (nom == null || nom.isBlank()) {
            clubParametres.findByClubIdAndCle(clubId, ParametreIaService.CLE_NOM_ASSISTANT)
                    .ifPresent(clubParametres::delete);
            return;
        }
        ClubParametre p = clubParametres.findByClubIdAndCle(clubId, ParametreIaService.CLE_NOM_ASSISTANT)
                .orElseGet(() -> {
                    ClubParametre n = new ClubParametre();
                    n.setClubId(clubId);
                    n.setCle(ParametreIaService.CLE_NOM_ASSISTANT);
                    return n;
                });
        p.setValeur(nom.trim());
        clubParametres.save(p);
    }

    /** Remplace le marqueur {@code {nom}} du prompt système par le nom résolu. */
    public String appliquer(String prompt, UUID clubId) {
        return prompt == null ? "" : prompt.replace("{nom}", pour(clubId));
    }
}
