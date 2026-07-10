package com.remipreparateur.documentadmin.service;

import com.remipreparateur.documentadmin.entity.CategorieAge;
import com.remipreparateur.documentadmin.entity.TypeDocumentRequis;
import com.remipreparateur.documentadmin.repository.CategorieAgeRepository;
import com.remipreparateur.documentadmin.repository.TypeDocumentRequisRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Seed du référentiel documentaire par défaut d'un club neuf (catégories d'âge + types de
 * documents JOUEUR/STAFF).
 *
 * <p>Les migrations V47 (catégories + 4 docs JOUEUR) et V49 (3 docs STAFF) ne seedent que les
 * clubs EXISTANT au moment de leur exécution (INSERT … FROM club figé). Un club créé APRÈS
 * (via {@code ClubService.creerClubAvecPresident}) naîtrait sans référentiel : ce seeder est le
 * chemin go-forward qui reproduit exactement le même contenu.
 *
 * <p><b>Source de vérité</b> : V47__documents_administratifs.sql et V49__fiche_personne_club_level.sql.
 * Toute évolution des valeurs par défaut doit rester alignée ici ET là-bas.
 *
 * <p>Context-free (clubId explicite, pas de {@code ScopeResolver}) : appelable dans la transaction
 * de création du club. Idempotent (garde par code) : rejouable sans créer de doublon.
 */
@Service
public class ReferentielDocumentAdminSeeder {

    private final CategorieAgeRepository categorieAgeRepository;
    private final TypeDocumentRequisRepository typeDocumentRequisRepository;

    public ReferentielDocumentAdminSeeder(CategorieAgeRepository categorieAgeRepository,
                                          TypeDocumentRequisRepository typeDocumentRequisRepository) {
        this.categorieAgeRepository = categorieAgeRepository;
        this.typeDocumentRequisRepository = typeDocumentRequisRepository;
    }

    /** cf. V47 : catégories d'âge par défaut (bornes = âge atteint dans la saison, inclusif). */
    private record SeedCategorie(String code, String libelle, Short ageMin, Short ageMax, short ordre) {}

    /** cf. V47 (cible JOUEUR) et V49 (cible STAFF) : types de documents requis par défaut. */
    private record SeedTypeDoc(String code, String libelle, String description, boolean obligatoire,
                               boolean validationManuelle, Short dureeValiditeMois, String categoriesAge,
                               String cible, short ordre) {}

    private static final List<SeedCategorie> CATEGORIES = List.of(
            new SeedCategorie("U9",     "Moins de 9 ans",  null,     (short) 9,  (short) 1),
            new SeedCategorie("U11",    "Moins de 11 ans", (short) 10, (short) 11, (short) 2),
            new SeedCategorie("U13",    "Moins de 13 ans", (short) 12, (short) 13, (short) 3),
            new SeedCategorie("U15",    "Moins de 15 ans", (short) 14, (short) 15, (short) 4),
            new SeedCategorie("U17",    "Moins de 17 ans", (short) 16, (short) 17, (short) 5),
            new SeedCategorie("U19",    "Moins de 19 ans", (short) 18, (short) 19, (short) 6),
            new SeedCategorie("SENIOR", "Senior",          (short) 20, null,      (short) 7)
    );

    private static final List<SeedTypeDoc> TYPES_DOCUMENTS = List.of(
            // ── Documents JOUEUR (V47) ──
            new SeedTypeDoc("licence", "Licence FFF",
                    "Licence fédérale de la saison en cours",
                    true, true, (short) 12, null, "JOUEUR", (short) 1),
            new SeedTypeDoc("certificat_medical", "Certificat médical",
                    "Certificat de non contre-indication à la pratique du football",
                    true, true, (short) 12, null, "JOUEUR", (short) 2),
            new SeedTypeDoc("autorisation_parentale", "Autorisation parentale",
                    "Autorisation parentale de pratique et de déplacement (joueurs mineurs)",
                    true, true, null, "U9,U11,U13,U15,U17", "JOUEUR", (short) 3),
            new SeedTypeDoc("piece_identite", "Pièce d'identité",
                    "Copie recto-verso d'une pièce d'identité en cours de validité",
                    true, true, null, null, "JOUEUR", (short) 4),
            // ── Documents STAFF (V49) ──
            new SeedTypeDoc("licence_dirigeant", "Licence dirigeant/éducateur",
                    "Licence fédérale d'encadrant de la saison en cours",
                    true, true, (short) 12, null, "STAFF", (short) 20),
            new SeedTypeDoc("diplome_encadrement", "Diplôme d'encadrement",
                    "Diplôme d'entraîneur / éducateur (BEF, BMF, CFF…)",
                    false, true, null, null, "STAFF", (short) 21),
            new SeedTypeDoc("honorabilite", "Contrôle d'honorabilité",
                    "Attestation de contrôle d'honorabilité (obligatoire pour tout encadrant)",
                    true, true, (short) 12, null, "STAFF", (short) 22)
    );

    /**
     * Pose le référentiel par défaut sur le club donné. Idempotent : chaque code déjà présent est
     * ignoré (permet de rejouer/réparer sans doublon, contrainte d'unicité (club_id, code) oblige).
     */
    @Transactional
    public void seederReferentielParDefaut(UUID clubId) {
        for (SeedCategorie s : CATEGORIES) {
            if (categorieAgeRepository.findByClubIdAndCodeIgnoreCase(clubId, s.code()).isPresent()) continue;
            CategorieAge c = new CategorieAge();
            c.setClubId(clubId);
            c.setCode(s.code());
            c.setLibelle(s.libelle());
            c.setAgeMin(s.ageMin());
            c.setAgeMax(s.ageMax());
            c.setOrdre(s.ordre());
            c.setActif(true);
            categorieAgeRepository.save(c);
        }

        for (SeedTypeDoc s : TYPES_DOCUMENTS) {
            if (typeDocumentRequisRepository.findByClubIdAndCodeIgnoreCase(clubId, s.code()).isPresent()) continue;
            TypeDocumentRequis t = new TypeDocumentRequis();
            t.setClubId(clubId);
            t.setCode(s.code());
            t.setLibelle(s.libelle());
            t.setDescription(s.description());
            t.setObligatoire(s.obligatoire());
            t.setValidationManuelle(s.validationManuelle());
            t.setDureeValiditeMois(s.dureeValiditeMois());
            t.setCategoriesAge(s.categoriesAge());
            t.setCible(s.cible());
            t.setOrdre(s.ordre());
            t.setActif(true);
            typeDocumentRequisRepository.save(t);
        }
    }
}
