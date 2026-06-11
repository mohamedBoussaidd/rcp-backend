package com.remipreparateur.performance.seance.controller;

import com.remipreparateur.performance.seance.entity.TypeSeance;
import com.remipreparateur.performance.seance.repository.TypeSeanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/type-seances")
@RequiredArgsConstructor
public class TypeSeanceController {

    private final TypeSeanceRepository typeSeanceRepository;

    @GetMapping
    public List<TypeSeance> getAll() {
        return typeSeanceRepository.findAll();
    }
}
