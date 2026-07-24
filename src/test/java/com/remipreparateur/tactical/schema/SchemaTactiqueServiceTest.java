package com.remipreparateur.tactical.schema;

import com.remipreparateur.auth.entity.Role;
import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.auth.repository.UtilisateurRepository;
import com.remipreparateur.club.entity.Club;
import com.remipreparateur.club.repository.ClubRepository;
import com.remipreparateur.shared.security.CurrentUserProvider;
import com.remipreparateur.tactical.schema.dto.SchemaTactiqueDtos.SchemaRechercheResponse;
import com.remipreparateur.tactical.schema.dto.SchemaTactiqueDtos.SchemaTactiqueRequest;
import com.remipreparateur.tactical.schema.dto.SchemaTactiqueDtos.SchemaTactiqueResponse;
import com.remipreparateur.tactical.schema.entity.SchemaTactique;
import com.remipreparateur.tactical.schema.repository.SchemaTactiqueRepository;
import com.remipreparateur.tactical.schema.service.SchemaTactiqueService;
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

/**
 * Couvre les briques « schémas globaux » : création réservée au super-admin, et surtout la
 * PROMOTION d'un schéma de club — qui doit dupliquer en global sans jamais toucher l'original.
 */
class SchemaTactiqueServiceTest {

    private final SchemaTactiqueRepository schemaRepo = mock(SchemaTactiqueRepository.class);
    private final UtilisateurRepository userRepo = mock(UtilisateurRepository.class);
    private final ClubRepository clubRepo = mock(ClubRepository.class);
    private final CurrentUserProvider currentUser = mock(CurrentUserProvider.class);

    private final SchemaTactiqueService service =
            new SchemaTactiqueService(schemaRepo, userRepo, clubRepo, currentUser);

    /** Branche {@code currentUser.current()} sur un utilisateur du rôle donné (stub en une étape). */
    private void connecte(Role role) {
        Utilisateur u = mock(Utilisateur.class);
        when(u.getRole()).thenReturn(role);
        when(u.getId()).thenReturn(UUID.randomUUID());
        when(currentUser.current()).thenReturn(u);
    }

    @Test
    void creerGlobal_parSuperAdmin_estSansClubEtFourni() {
        connecte(Role.SUPER_ADMIN);
        when(schemaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SchemaTactiqueResponse res = service.creerGlobal(
                new SchemaTactiqueRequest("Pressing haut", "pressing", "{}", null, null));

        ArgumentCaptor<SchemaTactique> cap = ArgumentCaptor.forClass(SchemaTactique.class);
        verify(schemaRepo).save(cap.capture());
        assertThat(cap.getValue().getClubId()).isNull();
        assertThat(res.fourni()).isTrue();
        assertThat(res.nom()).isEqualTo("Pressing haut");
    }

    @Test
    void creerGlobal_parNonSuperAdmin_estRefuse() {
        connecte(Role.ENTRAINEUR);
        assertThatThrownBy(() -> service.creerGlobal(
                new SchemaTactiqueRequest("X", null, "{}", null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("super-admin");
        verify(schemaRepo, never()).save(any());
    }

    @Test
    void promouvoir_dupliqueEnGlobal_sansToucherLoriginal() {
        UUID sourceId = UUID.randomUUID();
        UUID clubId = UUID.randomUUID();
        SchemaTactique source = new SchemaTactique();
        source.setClubId(clubId);
        source.setNom("Sortie de balle");
        source.setCategorie("sortie_de_balle");
        source.setSchemaJson("{\"a\":1}");
        source.setApercu("img");

        connecte(Role.SUPER_ADMIN);
        when(schemaRepo.findById(sourceId)).thenReturn(Optional.of(source));
        when(schemaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SchemaTactiqueResponse res = service.promouvoir(sourceId);

        ArgumentCaptor<SchemaTactique> cap = ArgumentCaptor.forClass(SchemaTactique.class);
        verify(schemaRepo).save(cap.capture());
        SchemaTactique copie = cap.getValue();
        assertThat(copie).isNotSameAs(source);          // c'est une COPIE
        assertThat(copie.getClubId()).isNull();          // devenue globale
        assertThat(copie.getSchemaJson()).isEqualTo("{\"a\":1}");
        assertThat(res.fourni()).isTrue();
        // L'original n'est ni supprimé ni ré-attribué.
        assertThat(source.getClubId()).isEqualTo(clubId);
        verify(schemaRepo, never()).deleteById(any());
    }

    @Test
    void promouvoir_dunSchemaDejaGlobal_estRefuse() {
        UUID id = UUID.randomUUID();
        SchemaTactique global = new SchemaTactique();
        global.setClubId(null);
        connecte(Role.SUPER_ADMIN);
        when(schemaRepo.findById(id)).thenReturn(Optional.of(global));

        assertThatThrownBy(() -> service.promouvoir(id))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("déjà global");
        verify(schemaRepo, never()).save(any());
    }

    @Test
    void rechercher_filtreParNom_etResoudLeNomDuClub() {
        UUID clubId = UUID.randomUUID();
        SchemaTactique presse = new SchemaTactique();
        presse.setClubId(clubId);
        presse.setNom("Pressing médian");
        SchemaTactique corner = new SchemaTactique();
        corner.setClubId(clubId);
        corner.setNom("Corner offensif");

        Club club = mock(Club.class);
        when(club.getNom()).thenReturn("FC Test");
        connecte(Role.SUPER_ADMIN);
        when(schemaRepo.findByClubIdIsNotNullOrderByUpdatedAtDesc()).thenReturn(List.of(presse, corner));
        when(clubRepo.findById(clubId)).thenReturn(Optional.of(club));

        List<SchemaRechercheResponse> res = service.rechercher(null, "press", null);

        assertThat(res).hasSize(1);
        assertThat(res.get(0).nom()).isEqualTo("Pressing médian");
        assertThat(res.get(0).clubNom()).isEqualTo("FC Test");
    }

    @Test
    void rechercher_parNonSuperAdmin_estRefuse() {
        connecte(Role.PREPARATEUR);
        assertThatThrownBy(() -> service.rechercher(null, null, null))
                .isInstanceOf(ResponseStatusException.class);
    }
}
