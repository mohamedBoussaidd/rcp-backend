package com.remipreparateur.performance.importation;

import com.remipreparateur.club.entity.Equipe;
import com.remipreparateur.club.repository.EquipeRepository;
import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.joueur.service.JoueurService;
import com.remipreparateur.performance.importation.dto.AnalyseImportRpeResponse;
import com.remipreparateur.performance.importation.dto.ConfirmerImportRpeRequest;
import com.remipreparateur.performance.importation.dto.LigneRpeImportDto;
import com.remipreparateur.performance.importation.repository.AliasJoueurImportRepository;
import com.remipreparateur.performance.importation.service.ImportRpeService;
import com.remipreparateur.performance.importation.service.LecteurFichierTabulaire;
import com.remipreparateur.performance.rpe.entity.RpeSeance;
import com.remipreparateur.performance.rpe.repository.RpeSeanceRepository;
import com.remipreparateur.performance.seance.entity.Seance;
import com.remipreparateur.performance.seance.repository.SeanceRepository;
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
 * Vérifie le cœur de l'import RPE sur le VRAI fichier post-séance (mojibake inclus) :
 * détection des colonnes tolérante à l'encodage, parsing du RPE/plaisir, exclusion des
 * lignes sans réponse, appariement des noms et calcul de la charge sRPE = rpe × durée.
 * Mocks Mockito « lenient » (pas de MockitoExtension → aucun contrôle de stub inutilisé).
 */
class ImportRpeServiceTest {

    private final AliasJoueurImportRepository aliasRepo = mock(AliasJoueurImportRepository.class);
    private final SeanceRepository seanceRepo = mock(SeanceRepository.class);
    private final EquipeRepository equipeRepo = mock(EquipeRepository.class);
    private final JoueurRepository joueurRepo = mock(JoueurRepository.class);
    private final JoueurService joueurService = mock(JoueurService.class);
    private final RpeSeanceRepository rpeRepo = mock(RpeSeanceRepository.class);
    private final ScopeResolver scopeResolver = mock(ScopeResolver.class);
    private final LecteurFichierTabulaire lecteur = new LecteurFichierTabulaire();

    private final ImportRpeService service = new ImportRpeService(
            lecteur, aliasRepo, seanceRepo, equipeRepo, joueurRepo, joueurService, rpeRepo, scopeResolver);

    private final UUID seanceId = UUID.randomUUID();
    private final UUID equipeId = UUID.randomUUID();
    private final UUID clubId = UUID.randomUUID();

    private Seance seance;
    private Joueur agami, besson, blot;

    @BeforeEach
    void setup() {
        seance = mock(Seance.class);
        when(seance.getId()).thenReturn(seanceId);
        when(seance.getEquipeId()).thenReturn(equipeId);
        when(seance.getDate()).thenReturn(LocalDate.of(2026, 7, 13));
        when(seance.getDureeMinutes()).thenReturn((short) 90);
        when(seance.getStatut()).thenReturn("PLANIFIEE");
        when(seanceRepo.findById(seanceId)).thenReturn(Optional.of(seance));

        Equipe equipe = mock(Equipe.class);
        when(equipe.getClubId()).thenReturn(clubId);
        when(equipeRepo.findById(equipeId)).thenReturn(Optional.of(equipe));

        agami = joueur("Daniel", "Agami");
        besson = joueur("Gabin", "Besson");
        blot = joueur("Thomas", "Blot");
        when(joueurRepo.findByClubId(clubId)).thenReturn(List.of(agami, besson, blot));
        when(aliasRepo.findByClubId(clubId)).thenReturn(List.of());
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
        try (var in = getClass().getResourceAsStream("/import-rpe/entrainement-post-13-07.csv")) {
            return in.readAllBytes();
        }
    }

    @Test
    void analyse_detecte_colonnes_et_apparie() throws Exception {
        AnalyseImportRpeResponse res = service.analyser(csv(), "entrainement.csv", null, seanceId);

        assertThat(res.getStatut()).isEqualTo("PRET");
        assertThat(res.getDureeSeance()).isEqualTo((short) 90);
        assertThat(res.getLignes()).hasSize(24);
        assertThat(res.getNbRepondants()).isEqualTo(22);
        assertThat(res.getNbSansReponse()).isEqualTo(2);

        // Ligne appariée « Agami Daniel » (format NOM Prénom) : RPE 6, sRPE 540, plaisir 8.
        LigneRpeImportDto agamiLigne = ligne(res, "Agami Daniel");
        assertThat(agamiLigne.getRpe()).isEqualTo((short) 6);
        assertThat(agamiLigne.getCharge()).isEqualTo(540);
        assertThat(agamiLigne.getPlaisir()).isEqualTo((short) 8);
        assertThat(agamiLigne.getJoueurId()).isNotNull();
        assertThat(agamiLigne.isRepondu()).isTrue();

        // Ligne sans réponse (Dumas) : non importée.
        LigneRpeImportDto dumas = ligne(res, "Dumas Aymeric");
        assertThat(dumas.isRepondu()).isFalse();
        assertThat(dumas.getRpe()).isNull();

        // 3 fiches seedées appariées ; les 19 autres répondants restent inconnus (dédupliqués).
        long appariees = res.getLignes().stream().filter(l -> l.getJoueurId() != null).count();
        assertThat(appariees).isEqualTo(3);
        assertThat(res.getJoueursInconnus()).hasSize(19);
    }

    @Test
    void confirmer_upsert_charge_et_plaisir() throws Exception {
        AnalyseImportRpeResponse res = service.analyser(csv(), "entrainement.csv", null, seanceId);

        ConfirmerImportRpeRequest req = new ConfirmerImportRpeRequest();
        req.setSeanceId(seanceId.toString());
        req.setResolutions(List.of());
        req.setLignes(res.getLignes().stream().filter(LigneRpeImportDto::isRepondu).toList());

        when(joueurRepo.findById(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return Stream.of(agami, besson, blot).filter(j -> j.getId().equals(id)).findFirst();
        });
        when(rpeRepo.findByJoueurIdAndSeanceId(any(), any())).thenReturn(Optional.empty());
        when(rpeRepo.save(any(RpeSeance.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.confirmer(req);

        // 3 appariés importés ; 19 inconnus (sans joueurId ni résolution) ignorés.
        assertThat(result.get("inseres")).isEqualTo(3);
        assertThat(result.get("ignores")).isEqualTo(19);

        ArgumentCaptor<RpeSeance> cap = ArgumentCaptor.forClass(RpeSeance.class);
        verify(rpeRepo, times(3)).save(cap.capture());
        RpeSeance agamiRpe = cap.getAllValues().stream()
                .filter(r -> agami.getId().equals(r.getJoueurId())).findFirst().orElseThrow();
        assertThat(agamiRpe.getRpe()).isEqualTo((short) 6);
        assertThat(agamiRpe.getCharge()).isEqualTo(540);           // sRPE = 6 × 90
        assertThat(agamiRpe.getPlaisir()).isEqualTo((short) 8);
        assertThat(agamiRpe.getDureeMinutes()).isEqualTo((short) 90);
        assertThat(agamiRpe.getEquipeId()).isEqualTo(equipeId);
    }

    private LigneRpeImportDto ligne(AnalyseImportRpeResponse res, String identite) {
        return res.getLignes().stream()
                .filter(l -> l.getIdentiteFichier().equals(identite)).findFirst().orElseThrow();
    }
}
