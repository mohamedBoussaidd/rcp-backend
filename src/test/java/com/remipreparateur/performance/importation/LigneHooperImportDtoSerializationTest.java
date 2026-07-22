package com.remipreparateur.performance.importation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remipreparateur.performance.importation.dto.LigneHooperImportDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garde-fou de sérialisation JSON du champ {@code repondu} (même piège Lombok que l'import RPE :
 * un champ {@code aXxx} sortirait sous une clé démanglée « axxx » ≠ nom de champ, et le front
 * grisait alors toutes les lignes). Ce test échoue si un renommage futur réintroduit le piège.
 */
class LigneHooperImportDtoSerializationTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void repondu_sort_sous_une_cle_json_unique_et_propre() throws Exception {
        LigneHooperImportDto dto = new LigneHooperImportDto();
        dto.setRepondu(true);

        String json = om.writeValueAsString(dto);

        assertThat(json).contains("\"repondu\":true");
        assertThat(json).doesNotContain("arepondu");
        assertThat(json).doesNotContain("aRepondu");
    }

    @Test
    void repondu_fait_un_aller_retour_json_fidele() throws Exception {
        LigneHooperImportDto dto = new LigneHooperImportDto();
        dto.setRepondu(true);

        LigneHooperImportDto relu = om.readValue(om.writeValueAsString(dto), LigneHooperImportDto.class);

        assertThat(relu.isRepondu()).isTrue();
    }
}
