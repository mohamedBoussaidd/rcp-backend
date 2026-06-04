package com.remipreparateur.controller;

import com.remipreparateur.dto.ExerciceDtos.ExerciceRequest;
import com.remipreparateur.dto.ExerciceDtos.ExerciceResponse;
import com.remipreparateur.service.ExerciceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Bibliotheque d'exercices (niveau club).
 * Lecture : staff (scope club). Ecriture : entraineur + super-admin.
 * Edition/suppression : createur, ou president/super-admin (verifie dans le service).
 */
@RestController
@RequestMapping("/api/exercices")
public class ExerciceController {

    private final ExerciceService exerciceService;

    public ExerciceController(ExerciceService exerciceService) {
        this.exerciceService = exerciceService;
    }

    @GetMapping
    public List<ExerciceResponse> lister() {
        return exerciceService.lister();
    }

    @PostMapping
    public ResponseEntity<ExerciceResponse> creer(@Valid @RequestBody ExerciceRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(exerciceService.creer(req));
    }

    @PutMapping("/{id}")
    public ExerciceResponse modifier(@PathVariable UUID id, @Valid @RequestBody ExerciceRequest req) {
        return exerciceService.modifier(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID id) {
        exerciceService.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
