package com.remipreparateur.performance.importation;

import com.remipreparateur.club.entity.Equipe;
import com.remipreparateur.club.repository.EquipeRepository;
import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.joueur.service.JoueurService;
import com.remipreparateur.medical.wellness.entity.WellnessQuotidien;
import com.remipreparateur.medical.wellness.repository.WellnessQuotidienRepository;
import com.remipreparateur.performance.importation.dto.AnalyseImportHooperResponse;
import com.remipreparateur.performance.importation.dto.ConfirmerImportHooperRequest;
import com.remipreparateur.performance.importation.dto.LigneHooperImportDto;
import com.remipreparateur.performance.importation.repository.AliasJoueurImportRepository;
import com.remipreparateur.performance.importation.service.ImportHooperService;
import com.remipreparateur.performance.importation.service.LecteurFichierTabulaire;
import com.remipreparateur.saison.service.AppartenanceService;
import com.remipreparateur.shared.security.ScopeResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Vérifie le cœur de l'import Hooper sur le VRAI export « playermonitoring » (mojibake inclus) :
 * détection des colonnes, INVERSION d'échelle (export 10 = bon → app 1 = bon), stress neutre,
 * gêne localisée (0/vide = aucune), date de séance retenue (pas la date de réponse), exclusion
 * des non-répondants, et préservation du stress d'une saisie existante.
 * Mocks Mockito « lenient » (pas de MockitoExtension → aucun contrôle de stub inutilisé).
 */
class ImportHooperServiceTest {

    private final AliasJoueurImportRepository aliasRepo = mock(AliasJoueurImportRepository.class);
    private final EquipeRepository equipeRepo = mock(EquipeRepository.class);
    private final JoueurRepository joueurRepo = mock(JoueurRepository.class);
    private final JoueurService joueurService = mock(JoueurService.class);
    private final WellnessQuotidienRepository wellnessRepo = mock(WellnessQuotidienRepository.class);
    private final AppartenanceService appartenance = mock(AppartenanceService.class);
    private final ScopeResolver scopeResolver = mock(ScopeResolver.class);
    private final LecteurFichierTabulaire lecteur = new LecteurFichierTabulaire();

    private final ImportHooperService service = new ImportHooperService(
            lecteur, aliasRepo, equipeRepo, joueurRepo, joueurService, wellnessRepo, appartenance, scopeResolver);

    private final UUID equipeId = UUID.randomUUID();
    private final UUID clubId = UUID.randomUUID();
    private final LocalDate dateSeance = LocalDate.of(2026, 7, 21);

    private Joueur agami, besson, feneuil, tissot;

    @BeforeEach
    void setup() {
        Equipe equipe = mock(Equipe.class);
        when(equipe.getClubId()).thenReturn(clubId);
        when(equipeRepo.findById(equipeId)).thenReturn(Optional.of(equipe));

        agami = joueur("Daniel", "Agami");
        besson = joueur("Gabin", "Besson");
        feneuil = joueur("Brian", "Feneuil");
        tissot = joueur("Isidore", "Tissot");
        when(joueurRepo.findByClubId(clubId)).thenReturn(List.of(agami, besson, feneuil, tissot));
        when(aliasRepo.findByClubId(clubId)).thenReturn(List.of());
        when(appartenance.equipePrincipale(any())).thenReturn(equipeId);
    }

    private Joueur joueur(String prenom, String nom) {
        Joueur j = mock(Joueur.class);
        when(j.getId()).thenReturn(UUID.randomUUID());
        when(j.getPrenom()).thenReturn(prenom);
        when(j.getNom()).thenReturn(nom);
        when(j.getClubId()).thenReturn(clubId);
        return j;
    }

    private byte[] csv() throws Exception {
        try (var in = getClass().getResourceAsStream("/import-hooper/playermonitoring-21-07.csv")) {
            return in.readAllBytes();
        }
    }

    @Test
    void analyse_convertit_inverse_et_apparie() throws Exception {
        AnalyseImportHooperResponse res = service.analyser(csv(), "playermonitoring.csv", null, equipeId);

        assertThat(res.getStatut()).isEqualTo("PRET");
        assertThat(res.getEquipeId()).isEqualTo(equipeId.toString());
        assertThat(res.getLignes()).hasSize(24);
        assertThat(res.getNbRepondants()).isEqualTo(23);
        assertThat(res.getNbSansReponse()).isEqualTo(1);

        // Agami : export E8/F8/H8/S8 → inversion 11−8 = 3 partout ; stress neutre 5 ; pas de gêne.
        LigneHooperImportDto a = ligne(res, "Agami Daniel");
        assertThat(a.isRepondu()).isTrue();
        assertThat(a.getSommeil()).isEqualTo((short) 3);
        assertThat(a.getFatigue()).isEqualTo((short) 3); // Energie Générale → fatigue
        assertThat(a.getDouleur()).isEqualTo((short) 3); // Fraîcheur Musculaire → douleur
        assertThat(a.getHumeur()).isEqualTo((short) 3);
        assertThat(a.getStress()).isEqualTo((short) 5);
        assertThat(a.getGeneZone()).isNull();
        assertThat(a.getDate()).isEqualTo(dateSeance);
        assertThat(a.getJoueurId()).isNotNull();

        // Feneuil : Sommeil 10 → 1 ; gêne « Mollet » intensité 2.
        LigneHooperImportDto f = ligne(res, "Feneuil Brian");
        assertThat(f.getSommeil()).isEqualTo((short) 1);
        assertThat(f.getGeneZone()).isEqualTo("Mollet");
        assertThat(f.getGeneIntensite()).isEqualTo((short) 2);

        // Tissot : emplacement « Cheville » mais Douleur 0 → PAS de gêne enregistrée.
        LigneHooperImportDto t = ligne(res, "Tissot Isidore");
        assertThat(t.getGeneZone()).isNull();
        assertThat(t.getGeneIntensite()).isNull();

        // Laine a répondu le 22/07 pour la séance du 21/07 → on retient la date de séance.
        LigneHooperImportDto laine = ligne(res, "Laine Noam");
        assertThat(laine.getDate()).isEqualTo(dateSeance);

        // Dumas : tout vide → non-répondant, non importé.
        LigneHooperImportDto dumas = ligne(res, "Dumas Aymeric");
        assertThat(dumas.isRepondu()).isFalse();
        assertThat(dumas.getSommeil()).isNull();

        // 4 fiches seedées appariées ; les 19 autres répondants restent inconnus (dédupliqués).
        long appariees = res.getLignes().stream().filter(l -> l.getJoueurId() != null).count();
        assertThat(appariees).isEqualTo(4);
        assertThat(res.getJoueursInconnus()).hasSize(19);
    }

    @Test
    void confirmer_upsert_wellness_avec_valeurs_converties() throws Exception {
        AnalyseImportHooperResponse res = service.analyser(csv(), "playermonitoring.csv", null, equipeId);

        ConfirmerImportHooperRequest req = new ConfirmerImportHooperRequest();
        req.setEquipeId(equipeId.toString());
        req.setResolutions(List.of());
        req.setLignes(res.getLignes().stream().filter(LigneHooperImportDto::isRepondu).toList());

        when(joueurRepo.findById(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return Stream.of(agami, besson, feneuil, tissot).filter(j -> j.getId().equals(id)).findFirst();
        });
        when(wellnessRepo.findByJoueurIdAndDate(any(), any())).thenReturn(Optional.empty());
        when(wellnessRepo.save(any(WellnessQuotidien.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.confirmer(req);

        // 4 appariés importés ; 19 inconnus (sans joueurId ni résolution) ignorés.
        assertThat(result.get("inseres")).isEqualTo(4);
        assertThat(result.get("ignores")).isEqualTo(19);

        ArgumentCaptor<WellnessQuotidien> cap = ArgumentCaptor.forClass(WellnessQuotidien.class);
        verify(wellnessRepo, times(4)).save(cap.capture());

        WellnessQuotidien feneuilW = cap.getAllValues().stream()
                .filter(w -> feneuil.getId().equals(w.getJoueurId())).findFirst().orElseThrow();
        assertThat(feneuilW.getSommeil()).isEqualTo((short) 1);
        assertThat(feneuilW.getFatigue()).isEqualTo((short) 4);   // 11 − 7
        assertThat(feneuilW.getDouleur()).isEqualTo((short) 4);   // 11 − 7
        assertThat(feneuilW.getHumeur()).isEqualTo((short) 1);    // 11 − 10
        assertThat(feneuilW.getStress()).isEqualTo((short) 5);    // neutre
        assertThat(feneuilW.getGeneZone()).isEqualTo("Mollet");
        assertThat(feneuilW.getGeneIntensite()).isEqualTo((short) 2);
        assertThat(feneuilW.getDate()).isEqualTo(dateSeance);
        assertThat(feneuilW.getEquipeId()).isEqualTo(equipeId);

        WellnessQuotidien tissotW = cap.getAllValues().stream()
                .filter(w -> tissot.getId().equals(w.getJoueurId())).findFirst().orElseThrow();
        assertThat(tissotW.getGeneZone()).isNull(); // Douleur 0 → aucune gêne
    }

    @Test
    void confirmer_preserve_le_stress_et_la_gene_d_une_saisie_existante() throws Exception {
        AnalyseImportHooperResponse res = service.analyser(csv(), "playermonitoring.csv", null, equipeId);
        LigneHooperImportDto ligneAgami = ligne(res, "Agami Daniel");

        ConfirmerImportHooperRequest req = new ConfirmerImportHooperRequest();
        req.setEquipeId(equipeId.toString());
        req.setResolutions(List.of());
        req.setLignes(List.of(ligneAgami));

        // Saisie PWA déjà présente ce jour-là : stress réel 9 + gêne déclarée.
        WellnessQuotidien existant = new WellnessQuotidien();
        existant.setJoueurId(agami.getId());
        existant.setDate(dateSeance);
        existant.setSommeil((short) 6);
        existant.setFatigue((short) 6);
        existant.setDouleur((short) 6);
        existant.setStress((short) 9);
        existant.setHumeur((short) 6);
        existant.setGeneZone("Genou gauche");
        existant.setGeneIntensite((short) 5);

        when(joueurRepo.findById(agami.getId())).thenReturn(Optional.of(agami));
        when(wellnessRepo.findByJoueurIdAndDate(agami.getId(), dateSeance)).thenReturn(Optional.of(existant));
        when(wellnessRepo.save(any(WellnessQuotidien.class))).thenAnswer(inv -> inv.getArgument(0));

        service.confirmer(req);

        ArgumentCaptor<WellnessQuotidien> cap = ArgumentCaptor.forClass(WellnessQuotidien.class);
        verify(wellnessRepo).save(cap.capture());
        WellnessQuotidien saved = cap.getValue();
        assertThat(saved.getSommeil()).isEqualTo((short) 3);       // écrasé par l'import
        assertThat(saved.getStress()).isEqualTo((short) 9);        // stress PWA PRÉSERVÉ
        assertThat(saved.getGeneZone()).isEqualTo("Genou gauche"); // gêne PWA PRÉSERVÉE (import sans gêne)
    }

    private LigneHooperImportDto ligne(AnalyseImportHooperResponse res, String identite) {
        return res.getLignes().stream()
                .filter(l -> l.getIdentiteFichier().equals(identite)).findFirst().orElseThrow();
    }
}
