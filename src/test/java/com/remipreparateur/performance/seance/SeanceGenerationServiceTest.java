package com.remipreparateur.performance.seance;

import com.remipreparateur.auth.entity.Role;
import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.ia.service.LlmService;
import com.remipreparateur.performance.seance.entity.ReferentielSousPrincipe;
import com.remipreparateur.performance.seance.entity.TypeSeance;
import com.remipreparateur.performance.seance.repository.ReferentielSousPrincipeRepository;
import com.remipreparateur.performance.seance.repository.TypeSeanceRepository;
import com.remipreparateur.performance.seance.service.SeanceGenerationService;
import com.remipreparateur.performance.seance.service.SeanceGenerationService.SeanceBrouillon;
import com.remipreparateur.shared.security.CurrentUserProvider;
import com.remipreparateur.tactical.exercice.entity.Exercice;
import com.remipreparateur.tactical.exercice.repository.ExerciceRepository;
import com.remipreparateur.tactical.importphoto.service.ParametreIaService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Vérifie que le catalogue envoyé au LLM est bien ENRICHI (dominantes dosées, thèmes de jeu,
 * intensité, durée + légende) — c'est le levier de qualité du générateur — et que le brouillon
 * renvoyé réapparie les exercices existants via leur id.
 */
class SeanceGenerationServiceTest {

    private final LlmService llm = mock(LlmService.class);
    private final ExerciceRepository exerciceRepo = mock(ExerciceRepository.class);
    private final TypeSeanceRepository typeRepo = mock(TypeSeanceRepository.class);
    private final ReferentielSousPrincipeRepository sousPrincipeRepo = mock(ReferentielSousPrincipeRepository.class);
    private final ParametreIaService parametres = mock(ParametreIaService.class);
    private final CurrentUserProvider currentUser = mock(CurrentUserProvider.class);

    private final SeanceGenerationService service = new SeanceGenerationService(
            llm, exerciceRepo, typeRepo, sousPrincipeRepo, parametres, currentUser);

    @Test
    void catalogue_estEnrichiDesTagsDominantesEtThemes_etReapparieLesExercices() {
        UUID clubId = UUID.randomUUID();
        Utilisateur coach = mock(Utilisateur.class);
        when(coach.getRole()).thenReturn(Role.ENTRAINEUR);
        when(coach.getClubId()).thenReturn(clubId);
        when(currentUser.current()).thenReturn(coach);

        UUID themeId = UUID.randomUUID();
        ReferentielSousPrincipe theme = new ReferentielSousPrincipe();
        theme.setId(themeId);
        theme.setLibelle("Conservation");
        when(sousPrincipeRepo.findAll()).thenReturn(List.of(theme));

        UUID exId = UUID.randomUUID();
        Exercice ex = new Exercice();
        ex.setId(exId);
        ex.setClubId(clubId);
        ex.setNom("Toro 5v2");
        ex.setForme("JEU_REDUIT");
        ex.setType("TECHNIQUE");
        ex.setIntensite((short) 3);
        ex.setDureeMinutes((short) 15);
        ex.setObjectif("Garder le ballon sous pression");
        ex.setDominanteTechniqueIntensite((short) 4);
        ex.setDominanteTactiqueFoncIntensite((short) 3);
        ex.setSousPrincipeIds(new java.util.ArrayList<>(List.of(themeId)));
        when(exerciceRepo.findByClubIdOrderByCreatedAtDesc(clubId)).thenReturn(List.of(ex));
        when(exerciceRepo.findByClubIdIsNullOrderByCreatedAtDesc()).thenReturn(List.of());

        TypeSeance type = mock(TypeSeance.class);
        when(type.getId()).thenReturn(UUID.randomUUID());
        when(type.getCode()).thenReturn("TECH");
        when(type.getLibelle()).thenReturn("Technique");
        when(typeRepo.findAll()).thenReturn(List.of(type));

        when(parametres.valeur(anyString())).thenReturn("PROMPT DE BASE");
        String reponse = "{\"titre\":\"Séance conservation\",\"typeCode\":\"TECH\",\"dureeMinutes\":75,"
                + "\"dominantes\":{\"technique\":4},\"blocs\":[{\"libelle\":\"Corps\",\"dureeMinutes\":40,"
                + "\"exercices\":[\"" + exId + "\"]}]}";
        when(llm.genererTexte(any(), anyString(), anyString(), anyString(), anyInt())).thenReturn(reponse);

        SeanceBrouillon res = service.generer("séance à dominante technique, conservation");

        // Le prompt système envoyé au LLM porte bien les tags enrichis.
        ArgumentCaptor<String> sys = ArgumentCaptor.forClass(String.class);
        verify(llm).genererTexte(any(), anyString(), sys.capture(), anyString(), anyInt());
        String prompt = sys.getValue();
        assertThat(prompt).contains(exId.toString());
        assertThat(prompt).contains("int=3");
        assertThat(prompt).contains("15min");
        assertThat(prompt).contains("dom: technique 4, tactiqueFonc 3");   // triées par dosage décroissant
        assertThat(prompt).contains("thèmes: Conservation");
        assertThat(prompt).contains("LÉGENDE DES TAGS");
        assertThat(prompt).contains("PROMPT DE BASE");

        // Le brouillon réapparie l'exercice existant via son id.
        assertThat(res.titre()).isEqualTo("Séance conservation");
        assertThat(res.blocs()).hasSize(1);
        assertThat(res.blocs().get(0).exerciceIds()).containsExactly(exId);
        assertThat(res.exercicesManquants()).isEmpty();
    }
}
