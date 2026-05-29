package com.remipreparateur.controller;

import com.remipreparateur.entity.ConfigParam;
import com.remipreparateur.repository.ConfigParamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/configuration")
@RequiredArgsConstructor
public class ConfigurationController {

    private final ConfigParamRepository configRepo;

    @GetMapping
    public Map<String, BigDecimal> getAll() {
        return configRepo.findAll().stream()
                .collect(Collectors.toMap(ConfigParam::getCle, ConfigParam::getValeur));
    }

    @PatchMapping("/{cle}")
    public ResponseEntity<Void> update(@PathVariable String cle,
                                       @RequestBody Map<String, BigDecimal> body) {
        return configRepo.findById(cle).map(c -> {
            c.setValeur(body.get("valeur"));
            c.setUpdatedAt(LocalDateTime.now());
            configRepo.save(c);
            return ResponseEntity.ok().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/reset/{cle}")
    public ResponseEntity<Void> resetOne(@PathVariable String cle) {
        return configRepo.findById(cle).map(c -> {
            c.setValeur(c.getValeurDefaut());
            c.setUpdatedAt(LocalDateTime.now());
            configRepo.save(c);
            return ResponseEntity.ok().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/reset-all")
    public ResponseEntity<Void> resetAll() {
        List<ConfigParam> all = configRepo.findAll();
        all.forEach(c -> {
            c.setValeur(c.getValeurDefaut());
            c.setUpdatedAt(LocalDateTime.now());
        });
        configRepo.saveAll(all);
        return ResponseEntity.ok().build();
    }
}
