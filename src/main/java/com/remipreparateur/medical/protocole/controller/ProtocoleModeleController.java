package com.remipreparateur.medical.protocole.controller;

import com.remipreparateur.medical.protocole.dto.ProtocoleModeleDtos.ModeleRequest;
import com.remipreparateur.medical.protocole.dto.ProtocoleModeleDtos.ModeleResponse;
import com.remipreparateur.medical.protocole.service.ProtocoleModeleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Bibliothèque de protocoles de reprise (RTP) du club.
 * Sous /api/protocoles-modeles : lecture blessures:read, écriture blessures:write (SecurityConfig).
 */
@RestController
@RequestMapping("/api/protocoles-modeles")
public class ProtocoleModeleController {

    private final ProtocoleModeleService service;

    public ProtocoleModeleController(ProtocoleModeleService service) {
        this.service = service;
    }

    @GetMapping
    public List<ModeleResponse> lister() {
        return service.lister();
    }

    @PostMapping
    public ResponseEntity<ModeleResponse> creer(@Valid @RequestBody ModeleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creer(req));
    }

    @PutMapping("/{id}")
    public ModeleResponse modifier(@PathVariable UUID id, @Valid @RequestBody ModeleRequest req) {
        return service.modifier(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/dupliquer")
    public ResponseEntity<ModeleResponse> dupliquer(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.dupliquer(id));
    }
}
