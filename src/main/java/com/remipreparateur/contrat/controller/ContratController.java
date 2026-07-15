package com.remipreparateur.contrat.controller;

import com.remipreparateur.contrat.dto.ContratDtos.ContratRequest;
import com.remipreparateur.contrat.dto.ContratDtos.ContratResponse;
import com.remipreparateur.contrat.dto.ContratDtos.ContratStats;
import com.remipreparateur.contrat.service.ContratService;
import com.remipreparateur.contrat.service.FichierContratStockage.Fichier;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Contrats du club (joueurs & staff). Gardé par contrats:manage (Président/Administratif —
 * cf. SecurityConfig) : la confidentialité prime, le reste du staff n'y accède pas.
 */
@RestController
@RequestMapping("/api/contrats")
public class ContratController {

    private final ContratService service;

    public ContratController(ContratService service) {
        this.service = service;
    }

    @GetMapping
    public List<ContratResponse> lister() {
        return service.lister();
    }

    @GetMapping("/stats")
    public ContratStats stats() {
        return service.stats();
    }

    @PostMapping
    public ResponseEntity<ContratResponse> creer(@Valid @RequestBody ContratRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creer(req));
    }

    @PutMapping("/{id}")
    public ContratResponse modifier(@PathVariable UUID id, @Valid @RequestBody ContratRequest req) {
        return service.modifier(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/fichier", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ContratResponse deposerFichier(@PathVariable UUID id, @RequestParam("fichier") MultipartFile fichier) {
        return service.deposerFichier(id, fichier);
    }

    @GetMapping("/{id}/fichier")
    public ResponseEntity<Resource> telecharger(@PathVariable UUID id) {
        return reponseFichier(service.chargerFichier(id));
    }

    static ResponseEntity<Resource> reponseFichier(Fichier f) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.typeMime()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + f.nomOriginal().replace("\"", "") + "\"")
                .body(f.resource());
    }
}
