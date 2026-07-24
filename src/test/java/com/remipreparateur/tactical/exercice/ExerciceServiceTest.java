package com.remipreparateur.tactical.exercice;

import com.remipreparateur.auth.entity.Role;
import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.auth.repository.UtilisateurRepository;
import com.remipreparateur.club.entity.Club;
import com.remipreparateur.club.repository.ClubRepository;
import com.remipreparateur.club.repository.EquipeRepository;
import com.remipreparateur.shared.security.CurrentUserProvider;
import com.remipreparateur.tactical.exercice.dto.ExerciceDtos.ExerciceRechercheResponse;
import com.remipreparateur.tactical.exercice.dto.ExerciceDtos.ExerciceResponse;
import com.remipreparateur.tactical.exercice.entity.Exercice;
import com.remipreparateur.tactical.exercice.repository.ExerciceRepository;
import com.remipreparateur.tactical.exercice.service.ExerciceService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Miroir « exercices globaux » : promotion d'un exercice de club en global (copie, original intact). */
class ExerciceServiceTest {

    private final ExerciceRepository exerciceRepo = mock(ExerciceRepository.class);
    private final UtilisateurRepository userRepo = mock(UtilisateurRepository.class);
    private final EquipeRepository equipeRepo = mock(EquipeRepository.class);
    private final ClubRepository clubRepo = mock(ClubRepository.class);
    private final CurrentUserProvider currentUser = mock(CurrentUserProvider.class);

    private final ExerciceService service =
            new ExerciceService(exerciceRepo, userRepo, equipeRepo, clubRepo, currentUser);

    /** Branche {@code currentUser.current()} sur un utilisateur du rôle donné (stub en une étape). */
    private void connecte(Role role) {
        Utilisateur u = mock(Utilisateur.class);
        when(u.getRole()).thenReturn(role);
        when(u.getId()).thenReturn(UUID.randomUUID());
        when(currentUser.current()).thenReturn(u);
    }

    @Test
    void promouvoir_dupliqueEnGlobal_sansToucherLoriginal() {
        UUID id = UUID.randomUUID();
        UUID clubId = UUID.randomUUID();
        Exercice source = new Exercice();
        source.setClubId(clubId);
        source.setNom("Toro 5v2");
        source.setSchemaJson("{\"t\":1}");

        connecte(Role.SUPER_ADMIN);
        when(exerciceRepo.findById(id)).thenReturn(Optional.of(source));
        when(exerciceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExerciceResponse res = service.promouvoir(id);

        ArgumentCaptor<Exercice> cap = ArgumentCaptor.forClass(Exercice.class);
        verify(exerciceRepo).save(cap.capture());
        Exercice copie = cap.getValue();
        assertThat(copie).isNotSameAs(source);
        assertThat(copie.getClubId()).isNull();
        assertThat(copie.getNom()).isEqualTo("Toro 5v2");
        assertThat(copie.getSchemaJson()).isEqualTo("{\"t\":1}");
        assertThat(res.global()).isTrue();
        assertThat(source.getClubId()).isEqualTo(clubId);   // original intact
        verify(exerciceRepo, never()).deleteById(any());
    }

    @Test
    void promouvoir_dunExerciceDejaGlobal_estRefuse() {
        UUID id = UUID.randomUUID();
        Exercice global = new Exercice();
        global.setClubId(null);
        connecte(Role.SUPER_ADMIN);
        when(exerciceRepo.findById(id)).thenReturn(Optional.of(global));

        assertThatThrownBy(() -> service.promouvoir(id))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("déjà global");
        verify(exerciceRepo, never()).save(any());
    }

    @Test
    void rechercher_filtreParFormeEtType_etResoudLeNomDuClub() {
        UUID clubId = UUID.randomUUID();
        Exercice jeuReduit = new Exercice();
        jeuReduit.setClubId(clubId);
        jeuReduit.setNom("JR 4v4");
        jeuReduit.setForme("JEU_REDUIT");
        jeuReduit.setType("MIXTE");
        Exercice analytique = new Exercice();
        analytique.setClubId(clubId);
        analytique.setNom("Passes");
        analytique.setForme("ANALYTIQUE");
        analytique.setType("TECHNIQUE");

        Club club = mock(Club.class);
        when(club.getNom()).thenReturn("FC Test");
        connecte(Role.SUPER_ADMIN);
        when(exerciceRepo.findByClubIdIsNotNullOrderByCreatedAtDesc()).thenReturn(List.of(jeuReduit, analytique));
        when(clubRepo.findById(clubId)).thenReturn(Optional.of(club));

        List<ExerciceRechercheResponse> res = service.rechercher(null, null, "JEU_REDUIT", null);

        assertThat(res).hasSize(1);
        assertThat(res.get(0).nom()).isEqualTo("JR 4v4");
        assertThat(res.get(0).clubNom()).isEqualTo("FC Test");
    }
}
