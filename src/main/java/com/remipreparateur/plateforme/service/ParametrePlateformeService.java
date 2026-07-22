package com.remipreparateur.plateforme.service;

import com.remipreparateur.plateforme.entity.ParametrePlateforme;
import com.remipreparateur.plateforme.repository.ParametrePlateformeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lecture/écriture des réglages plateforme (SUPER_ADMIN). Les clés sont semées en migration ;
 * la lecture retombe sur un défaut si une clé venait à manquer. Distinct des paramètres métier
 * {@code configuration} des clubs — cf. {@link ParametrePlateforme}.
 */
@Service
public class ParametrePlateformeService {

    public static final String CLE_RETENTION_LUES = "retention_notif_lues_jours";
    public static final String CLE_RETENTION_NON_LUES = "retention_notif_non_lues_jours";

    private final ParametrePlateformeRepository repository;

    public ParametrePlateformeService(ParametrePlateformeRepository repository) {
        this.repository = repository;
    }

    /** Valeur entière d'une clé, avec repli sur {@code defaut} si absente/vide. */
    @Transactional(readOnly = true)
    public int getInt(String cle, int defaut) {
        return repository.findById(cle)
                .map(ParametrePlateforme::getValeur)
                .map(BigDecimal::intValue)
                .orElse(defaut);
    }

    /** Tous les réglages (clé → {valeur, libellé}), pour l'écran super-admin. */
    @Transactional(readOnly = true)
    public Map<String, ParametrePlateforme> getAll() {
        Map<String, ParametrePlateforme> out = new LinkedHashMap<>();
        repository.findAll().forEach(p -> out.put(p.getCle(), p));
        return out;
    }

    /** Met à jour la valeur d'une clé existante (no-op silencieux si la clé n'existe pas). */
    @Transactional
    public void set(String cle, BigDecimal valeur) {
        repository.findById(cle).ifPresent(p -> {
            p.setValeur(valeur);
            p.setUpdatedAt(LocalDateTime.now());
            repository.save(p);
        });
    }
}
