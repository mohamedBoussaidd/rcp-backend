package com.remipreparateur.contrat.controller;

import com.remipreparateur.contrat.dto.ContratDtos.BulletinLigne;
import com.remipreparateur.contrat.dto.ContratDtos.DistributionResultat;
import com.remipreparateur.contrat.service.BulletinPaieService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Fiches de paye (transmission + suivi de distribution). Gardé par contrats:manage.
 * Flux : dépôt des PDFs de la période → « Distribuer » (notifications en lot) →
 * suivi déposé / notifié / téléchargé.
 */
@RestController
@RequestMapping("/api/bulletins-paie")
public class BulletinPaieController {

    private final BulletinPaieService service;

    public BulletinPaieController(BulletinPaieService service) {
        this.service = service;
    }

    /** Périodes existantes (1er jour du mois), plus récentes d'abord. */
    @GetMapping("/periodes")
    public List<LocalDate> periodes() {
        return service.periodes();
    }

    /** Tableau de suivi d'une période (?periode=yyyy-MM). */
    @GetMapping
    public List<BulletinLigne> lignes(@RequestParam String periode) {
        return service.lignes(periode);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BulletinLigne> deposer(@RequestParam UUID joueurId,
                                                 @RequestParam String periode,
                                                 @RequestParam("fichier") MultipartFile fichier) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.deposer(joueurId, periode, fichier));
    }

    /** Distribue la période : notifie chaque personne dont le bulletin n'a pas encore été notifié. */
    @PostMapping("/distribuer")
    public DistributionResultat distribuer(@RequestParam String periode) {
        return service.distribuer(periode);
    }

    @GetMapping("/{id}/fichier")
    public ResponseEntity<Resource> telecharger(@PathVariable UUID id) {
        return ContratController.reponseFichier(service.chargerFichier(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
