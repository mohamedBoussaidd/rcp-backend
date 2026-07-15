package com.remipreparateur.medical.protocole.service;

import com.remipreparateur.medical.protocole.entity.ProtocoleModele;
import com.remipreparateur.medical.protocole.entity.ProtocoleModeleEtape;
import com.remipreparateur.medical.protocole.repository.ProtocoleModeleEtapeRepository;
import com.remipreparateur.medical.protocole.repository.ProtocoleModeleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Seed du « Protocole standard » (réathlétisation en 4 phases) pour un club neuf.
 *
 * <p>La migration V57 ne seed que les clubs EXISTANT à son exécution : ce seeder est le chemin
 * go-forward appelé par {@code ClubService.creerClubAvecPresident} — même pattern que
 * {@code ReferentielDocumentAdminSeeder}. <b>Source de vérité</b> : V57__protocoles_reprise_qualification.sql.
 *
 * <p>Context-free (clubId explicite) et idempotent : ne fait rien si le club a déjà au moins un modèle.
 */
@Service
public class ProtocoleModeleSeeder {

    private record SeedEtape(String libelle, short jDebut, short jFin, String description) {}

    private static final List<SeedEtape> ETAPES_STANDARD = List.of(
            new SeedEtape("Phase 1 — Soins",                (short) 1,  (short) 5,  "Soins médicaux, glaçage, compression"),
            new SeedEtape("Phase 2 — Reprise individuelle", (short) 6,  (short) 12, "Course, renforcement musculaire, proprioception"),
            new SeedEtape("Phase 3 — Reprise collective",   (short) 13, (short) 18, "Entraînement avec le groupe sans contact"),
            new SeedEtape("Phase 4 — Retour compétition",   (short) 19, (short) 21, "Validation médicale, retour à la compétition"));

    private final ProtocoleModeleRepository modeleRepository;
    private final ProtocoleModeleEtapeRepository etapeRepository;

    public ProtocoleModeleSeeder(ProtocoleModeleRepository modeleRepository,
                                 ProtocoleModeleEtapeRepository etapeRepository) {
        this.modeleRepository = modeleRepository;
        this.etapeRepository = etapeRepository;
    }

    @Transactional
    public void seederProtocoleStandard(UUID clubId) {
        if (modeleRepository.existsByClubId(clubId)) return;
        ProtocoleModele m = new ProtocoleModele();
        m.setClubId(clubId);
        m.setNom("Protocole standard");
        m.setDescription("Réathlétisation progressive en 4 phases (protocole par défaut).");
        m.setActif(true);
        m.setOrdre((short) 1);
        modeleRepository.save(m);

        short ordre = 1;
        for (SeedEtape s : ETAPES_STANDARD) {
            ProtocoleModeleEtape e = new ProtocoleModeleEtape();
            e.setModeleId(m.getId());
            e.setOrdre(ordre++);
            e.setLibelle(s.libelle());
            e.setJDebut(s.jDebut());
            e.setJFin(s.jFin());
            e.setDescription(s.description());
            etapeRepository.save(e);
        }
    }
}
